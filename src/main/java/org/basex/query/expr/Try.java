package org.basex.query.expr;

import java.io.IOException;

import org.basex.io.serial.Serializer;
import org.basex.query.*;
import org.basex.query.item.Value;
import org.basex.query.iter.Iter;
import org.basex.query.util.*;
import org.basex.util.InputInfo;

/**
 * Project specific try/catch expression.
 *
 * @author BaseX Team 2005-12, BSD License
 * @author Christian Gruen
 */
public final class Try extends Single {
  /** Query exception. */
  private QueryException qe;
  /** Catches. */
  private final Catch[] ctch;

  /**
   * Constructor.
   * @param ii input info
   * @param t try expression
   * @param c catch expressions
   */
  public Try(final InputInfo ii, final Expr t, final Catch[] c) {
    super(ii, t);
    ctch = c;
  }

  @Override
  public Expr comp(final QueryContext ctx, final VarScope scp) throws QueryException {
    // check if none or all try/catch expressions are updating
    final Expr[] tmp = new Expr[ctch.length + 1];
    tmp[0] = expr;
    for(int c = 0; c < ctch.length; ++c) tmp[c + 1] = ctch[c].expr;
    checkUp(ctx, tmp);

    // compile expression
    try {
      super.comp(ctx, scp);
      // return value, which will never throw an error
      if(expr.isValue()) return expr;
    } catch(final QueryException ex) {
      // catch exception for evaluation if expression fails at compile time
      qe = ex;
    }

    // compile catch expressions
    for(final Catch c : ctch) c.comp(ctx, scp);

    // evaluate result type
    type = expr.type();
    for(final Catch c : ctch) type = type.intersect(c.type());
    return this;
  }

  @Override
  public Iter iter(final QueryContext ctx) throws QueryException {
    return value(ctx).iter();
  }

  @Override
  public Value value(final QueryContext ctx) throws QueryException {
    // don't catch errors from error handlers
    if(qe != null) return err(ctx, qe);
    try {
      return ctx.value(expr);
    } catch(final QueryException ex) {
      return err(ctx, ex);
    }
  }

  /**
   * Handles an exception.
   * @param ctx query context
   * @param ex query exception
   * @return result
   * @throws QueryException query exception
   */
  private Value err(final QueryContext ctx, final QueryException ex)
      throws QueryException {

    for(final Catch c : ctch) {
      final Value val = c.value(ctx, ex);
      if(val != null) return val;
    }
    throw ex;
  }

  @Override
  public boolean uses(final Use u) {
    for(final Catch c : ctch) if(c.uses(u)) return true;
    return super.uses(u);
  }

  @Override
  public boolean removable(final Var v) {
    for(final Catch c : ctch) if(!c.removable(v)) return false;
    return super.removable(v);
  }

  @Override
  public Expr remove(final Var v) {
    for(final Catch c : ctch) c.remove(v);
    return super.remove(v);
  }

  @Override
  public void plan(final Serializer ser) throws IOException {
    ser.openElement(this);
    expr.plan(ser);
    for(final Catch c : ctch) c.plan(ser);
    ser.closeElement();
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("try { " + expr + " }");
    for(final Catch c : ctch) sb.append(' ').append(c);
    return sb.toString();
  }

  @Override
  public boolean visitVars(final VarVisitor visitor) {
    return expr.visitVars(visitor) && visitor.visitAll(ctch);
  }
}
