package org.basex.query.expr;

import static org.basex.query.util.Err.*;

import java.io.IOException;

import org.basex.io.serial.Serializer;
import org.basex.query.*;
import org.basex.query.item.AtomType;
import org.basex.query.item.Item;
import org.basex.query.item.QNm;
import org.basex.query.item.Type;
import org.basex.query.iter.Iter;
import org.basex.query.util.*;
import org.basex.util.InputInfo;
import org.basex.util.TokenBuilder;
import org.basex.util.XMLToken;

/**
 * Abstract fragment constructor with a QName argument.
 *
 * @author BaseX Team 2005-12, BSD License
 * @author Christian Gruen
 */
public abstract class CName extends CFrag {
  /** Description. */
  private final String desc;
  /** QName. */
  Expr name;

  /**
   * Constructor.
   * @param d description
   * @param ii input info
   * @param n name
   * @param v attribute values
   */
  CName(final String d, final InputInfo ii, final Expr n,
        final Expr... v) {
    super(ii, v);
    name = n;
    desc = d;
  }

  @Override
  public CName comp(final QueryContext ctx, final VarScope scp) throws QueryException {
    super.comp(ctx, scp);
    name = checkUp(name, ctx).comp(ctx, scp);
    return this;
  }

  /**
   * Returns the atomized value of the constructor.
   * @param ctx query context
   * @param ii input info
   * @return resulting value
   * @throws QueryException query exception
   */
  final byte[] value(final QueryContext ctx, final InputInfo ii)
      throws QueryException {

    final TokenBuilder tb = new TokenBuilder();
    for(final Expr e : expr) {
      final Iter ir = ctx.iter(e);
      boolean m = false;
      for(Item it; (it = ir.next()) != null;) {
        if(m) tb.add(' ');
        tb.add(it.string(ii));
        m = true;
      }
    }
    return tb.finish();
  }

  /**
   * Returns an updated name expression.
   * @param ctx query context
   * @param ii input info
   * @return result
   * @throws QueryException query exception
   */
  final QNm qname(final QueryContext ctx, final InputInfo ii)
      throws QueryException {

    final Item it = checkItem(name, ctx);
    final Type ip = it.type;
    if(ip == AtomType.QNM) return (QNm) it;

    final byte[] str = it.string(ii);
    if(!XMLToken.isQName(str)) {
      (ip.isString() || ip.isUntyped() ? INVNAME : INVQNAME).thrw(input, str);
    }
    // create and update namespace
    final QNm nm = new QNm(str, ctx);
    if(!nm.hasURI() && nm.hasPrefix()) INVPREF.thrw(input, nm);
    return nm;

  }

  @Override
  public final Expr remove(final Var v) {
    name = name.remove(v);
    return super.remove(v);
  }

  @Override
  public final boolean uses(final Use u) {
    return name.uses(u) || super.uses(u);
  }

  @Override
  public final void plan(final Serializer ser) throws IOException {
    ser.openElement(this);
    name.plan(ser);
    for(final Expr e : expr) e.plan(ser);
    ser.closeElement();
  }

  @Override
  public final String description() {
    return info(desc);
  }

  @Override
  public final String toString() {
    return toString(desc + " { " + name + " }");
  }

  @Override
  public final boolean visitVars(final VarVisitor visitor) {
    return name.visitVars(visitor) && visitor.visitAll(expr);
  }
}
