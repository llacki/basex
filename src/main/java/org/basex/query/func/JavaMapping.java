package org.basex.query.func;

import static javax.xml.datatype.DatatypeConstants.*;
import static org.basex.query.QueryText.*;
import static org.basex.query.util.Err.*;
import static org.basex.util.Token.*;

import java.lang.reflect.*;
import java.lang.reflect.Array;
import java.math.*;
import java.net.*;

import javax.xml.datatype.*;
import javax.xml.namespace.*;

import org.basex.core.*;
import org.basex.query.*;
import org.basex.query.expr.*;
import org.basex.query.iter.*;
import org.basex.query.util.pkg.*;
import org.basex.query.value.*;
import org.basex.query.value.item.*;
import org.basex.query.value.seq.*;
import org.basex.query.value.type.*;
import org.basex.query.value.type.Type;
import org.basex.util.*;
import org.w3c.dom.*;
import org.w3c.dom.Text;

/**
 * This class contains common methods for executing Java code and mapping
 * Java objects to XQuery values.
 *
 * @author BaseX Team 2005-12, BSD License
 * @author Christian Gruen
 */
public abstract class JavaMapping extends Arr {
  /** New keyword. */
  static final String NEW = "new";
  /** Input Java types. */
  private static final Class<?>[] JAVA = {
    String.class,     boolean.class,  Boolean.class,      byte.class,
    Byte.class,       short.class,    Short.class,        int.class,
    Integer.class,    long.class,     Long.class,         float.class,
    Float.class,      double.class,   Double.class,       BigDecimal.class,
    BigInteger.class, QName.class,    char.class,         Character.class,
    URI.class,        URL.class
  };
  /** Resulting XQuery types. */
  private static final Type[] XQUERY = {
    AtomType.STR, AtomType.BLN, AtomType.BLN, AtomType.BYT,
    AtomType.BYT, AtomType.SHR, AtomType.SHR, AtomType.INT,
    AtomType.INT, AtomType.LNG, AtomType.LNG, AtomType.FLT,
    AtomType.FLT, AtomType.DBL, AtomType.DBL, AtomType.DEC,
    AtomType.ITR, AtomType.QNM, AtomType.STR, AtomType.STR,
    AtomType.URI, AtomType.URI
  };

  /**
   * Constructor.
   * @param ii input info
   * @param a arguments
   */
  JavaMapping(final InputInfo ii, final Expr[] a) {
    super(ii, a);
  }

  @Override
  public final Iter iter(final QueryContext ctx) throws QueryException {
    return value(ctx).iter();
  }

  @Override
  public final Value value(final QueryContext ctx) throws QueryException {
    final Value[] args = new Value[expr.length];
    for(int a = 0; a < expr.length; ++a) {
      args[a] = ctx.value(expr[a]);
      if(args[a].isEmpty()) XPEMPTY.thrw(info, description());
    }
    return toValue(eval(args, ctx));
  }

  /**
   * Returns the result of the evaluated Java function.
   * @param args arguments
   * @param ctx query context
   * @return arguments
   * @throws QueryException query exception
   */
  protected abstract Object eval(final Value[] args, final QueryContext ctx)
      throws QueryException;

  /**
   * Converts the specified result to an XQuery value.
   * @param res result object
   * @return value
   * @throws QueryException query exception
   */
  public static Value toValue(final Object res) throws QueryException {
    if(res == null) return Empty.SEQ;
    if(res instanceof Value) return (Value) res;
    if(res instanceof Iter) return ((Iter) res).value();
    // find XQuery mapping for specified type
    Type type = type(res);
    if(type != null) return type.cast(res, null);

    // character array: string
    if(res instanceof char[]) return Str.get(new String((char[]) res));

    // no array: return Java type
    if(!res.getClass().isArray()) return new Jav(res);
    final int s = Array.getLength(res);
    // empty array: empty sequence
    if(s == 0) return Empty.SEQ;

    // byte array: byte sequence
    if(res instanceof byte[]) {
      final byte[] values = (byte[]) res;
      return s > 1 ? new ByteSeq(values) : new Int(values[0], AtomType.BYT);
    }

    final Item[] items = new Item[s];
    if(res instanceof boolean[]) {
      type = AtomType.BLN;
      final boolean[] values = (boolean[]) res;
      for(int v = 0; v < s; v++) items[v] = Bln.get(values[v]);
    } else if(res instanceof short[]) {
      type = AtomType.SHR;
      final short[] values = (short[]) res;
      for(int v = 0; v < s; v++) items[v] = new Int(values[v], type);
    } else if(res instanceof int[]) {
      type = AtomType.INT;
      final int[] values = (int[]) res;
      for(int v = 0; v < s; v++) items[v] = new Int(values[v], type);
    } else if(res instanceof long[]) {
      type = AtomType.ITR;
      final long[] values = (long[]) res;
      for(int v = 0; v < s; v++) items[v] = Int.get(values[v]);
    } else if(res instanceof float[]) {
      type = AtomType.FLT;
      final float[] values = (float[]) res;
      for(int v = 0; v < s; v++) items[v] = Flt.get(values[v]);
    } else if(res instanceof double[]) {
      type = AtomType.DBL;
      final double[] values = (double[]) res;
      for(int v = 0; v < s; v++) items[v] = Dbl.get(values[v]);
    } else {
      final ValueBuilder vb = new ValueBuilder();
      for(final Object o : (Object[]) res) {
        vb.add(o instanceof Value ? (Value) o : new Jav(o));
      }
      return vb.value();
    }
    return Seq.get(items, s, type);
  }

  /**
   * Returns a new Java function instance.
   * @param qname function name
   * @param args arguments
   * @param ctx query context
   * @param ii input info
   * @return Java function, or {@code null}
   * @throws QueryException query exception
   */
  static JavaMapping get(final QNm qname, final Expr[] args, final QueryContext ctx,
      final InputInfo ii) throws QueryException {

    final byte[] uri = qname.uri();
    final byte[] ln = qname.local();
    // check if URI starts with "java:" prefix (if yes, module must be Java code)
    final boolean java = startsWith(uri, JAVAPREF);
    final QNm nm = new QNm(ln, java ? substring(uri, JAVAPREF.length) : uri);

    // rewrite function name: convert dashes to upper-case initials
    final TokenBuilder tb = new TokenBuilder();
    boolean dash = false;
    for(int p = 0; p < ln.length; p += cl(ln, p)) {
      final int ch = cp(ln, p);
      if(dash) {
        tb.add(Character.toUpperCase(ch));
        dash = false;
      } else {
        dash = ch == '-';
        if(!dash) tb.add(ch);
      }
    }
    final String name = tb.toString();

    // check imported Java modules
    String path = string(nm.uri());
    final String p = ModuleLoader.uri2path(path);
    if(p != null) path = p;
    path = path.replace("/", ".").substring(1);

    final Object jm  = ctx.modules.findImport(path);
    if(jm != null) {
      // find method with identical name and arity
      Method meth = null;
      for(final Method m : jm.getClass().getMethods()) {
        if(m.getName().equals(name) && m.getParameterTypes().length == args.length) {
          if(meth != null) JAVAAMB.thrw(ii, path + ':' + name);
          meth = m;
        }
      }

      if(meth != null) {
        // check if user has sufficient permissions to call the function
        Perm perm = Perm.ADMIN;
        final QueryModule.Requires req = meth.getAnnotation(QueryModule.Requires.class);
        if(req != null) perm = Perm.get(req.value().name());
        if(!ctx.context.user.has(perm)) return null;
        return new JavaModuleFunc(ii, jm, meth, args);
      }
      WHICHJAVA.thrw(ii, path + ':' + name);
    }

    // only allowed with administrator permissions
    if(!ctx.context.user.has(Perm.ADMIN)) return null;

    // check addressed class
    try {
      return new JavaFunc(ii, ctx.modules.findClass(path), name, args);
    } catch(final ClassNotFoundException ex) {
      // only throw exception if "java:" prefix was explicitly specified
      if(java) throw WHICHJAVA.thrw(ii, uri);
    } catch(final Throwable th) {
      Util.debug(th);
      throw INITJAVA.thrw(ii, th);
    }

    // no function found
    return null;
  }

  /**
   * Returns an appropriate XQuery data type for the specified Java object.
   * @param o object
   * @return xquery type, or {@code null} if no appropriate type was found
   */
  public static Type type(final Object o) {
    final Type t = type(o.getClass());
    if(t != null) return t;

    if(o instanceof Element) return NodeType.ELM;
    if(o instanceof Document) return NodeType.DOC;
    if(o instanceof DocumentFragment) return NodeType.DOC;
    if(o instanceof Attr) return NodeType.ATT;
    if(o instanceof Comment) return NodeType.COM;
    if(o instanceof ProcessingInstruction) return NodeType.PI;
    if(o instanceof Text) return NodeType.TXT;

    if(o instanceof Duration) {
      final Duration d = (Duration) o;
      return !d.isSet(YEARS) && !d.isSet(MONTHS) ? AtomType.DTD :
        !d.isSet(HOURS) && !d.isSet(MINUTES) && !d.isSet(SECONDS) ?
          AtomType.YMD : AtomType.DUR;
    }

    if(o instanceof XMLGregorianCalendar) {
      final QName type = ((XMLGregorianCalendar) o).getXMLSchemaType();
      if(type == DATE) return AtomType.DAT;
      if(type == DATETIME) return AtomType.DTM;
      if(type == TIME) return AtomType.TIM;
      if(type == GYEARMONTH) return AtomType.YMO;
      if(type == GMONTHDAY) return AtomType.MDA;
      if(type == GYEAR) return AtomType.YEA;
      if(type == GMONTH) return AtomType.MON;
      if(type == GDAY) return AtomType.DAY;
    }
    return null;
  }

  /**
   * Returns an appropriate XQuery data type for the specified Java class.
   * @param type Java type
   * @return xquery type
   */
  protected static Type type(final Class<?> type) {
    for(int j = 0; j < JAVA.length; ++j) {
      if(JAVA[j].isAssignableFrom(type)) return XQUERY[j];
    }
    return null;
  }

  /**
   * Returns a string representation of all found arguments.
   * @param args array with arguments
   * @return string representation
   */
  protected static String foundArgs(final Value[] args) {
    // compose found arguments
    final StringBuilder found = new StringBuilder();
    for(final Value a : args) {
      if(found.length() != 0) found.append(", ");
      found.append(a.type());
    }
    return found.toString();
  }

  @Override
  public boolean uses(final Use u) {
    return u == Use.NDT || super.uses(u);
  }
}
