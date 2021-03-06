package org.basex.query.flwor;

import static org.basex.query.QueryText.*;

import org.basex.query.*;
import org.basex.query.expr.*;
import org.basex.query.iter.*;
import org.basex.query.util.*;
import org.basex.query.value.*;
import org.basex.query.value.item.*;
import org.basex.query.value.node.*;
import org.basex.query.value.type.*;
import org.basex.util.*;
import org.basex.util.ft.*;

/**
 * Let clause.
 *
 * @author BaseX Team 2005-12, BSD License
 * @author Christian Gruen
 */
public final class Let extends ForLet {
  /** Scoring flag. */
  final boolean score;

  /**
   * Constructor.
   * @param ii input info
   * @param e variable input
   * @param v variable
   */
  public Let(final InputInfo ii, final Expr e, final Var v) {
    this(ii, e, v, false);
  }

  /**
   * Constructor.
   * @param ii input info
   * @param e variable input
   * @param v variable
   * @param s score flag
   */
  public Let(final InputInfo ii, final Expr e, final Var v, final boolean s) {
    super(ii, e, v);
    score = s;
  }

  @Override
  public Let compile(final QueryContext ctx) throws QueryException {
    expr = expr.compile(ctx);
    type = SeqType.ITEM;
    size = 1;
    var.size = expr.size();
    var.ret = score ? SeqType.DBL : expr.type();
    ctx.vars.add(var);
    return this;
  }

  @Override
  public Iter iter(final QueryContext ctx) {
    final Var vr = var.copy();

    return new Iter() {
      /** Variable stack size. */
      private int vs;
      /** Iterator flag. */
      private boolean more;

      @Override
      public Item next() throws QueryException {
        if(!more) {
          vs = ctx.vars.size();
          final Value v;
          if(score) {
            // assign average score value
            double s = 0;
            int c = 0;
            final Iter ir = ctx.iter(expr);
            for(Item it; (it = ir.next()) != null;) {
              s += it.score();
              ++c;
            }
            v = Dbl.get(Scoring.let(s, c));
          } else {
            v = ctx.value(expr);
          }
          ctx.vars.add(vr.bind(v, ctx));
          more = true;
          return Bln.TRUE;
        }
        reset();
        return null;
      }

      @Override
      public long size() {
        return 1;
      }

      @Override
      public Item get(final long i) throws QueryException {
        reset();
        return next();
      }

      @Override
      public boolean reset() {
        if(more) {
          ctx.vars.size(vs);
          more = false;
        }
        return true;
      }
    };
  }

  @Override
  boolean simple(final boolean one) {
    return !score;
  }

  @Override
  public void plan(final FElem plan) {
    addPlan(plan, planElem(score ? SCORE : VAR, var), expr);
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder(LET).append(' ');
    if(score) sb.append(SCORE).append(' ');
    sb.append(var).append(' ').append(ASSIGN).append(' ').append(expr);
    return sb.toString();
  }

  @Override
  public boolean declares(final Var v) {
    return var.is(v);
  }

  @Override
  public Var[] vars() {
    return new Var[]{ var };
  }
}
