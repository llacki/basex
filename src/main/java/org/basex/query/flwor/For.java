package org.basex.query.flwor;

import static org.basex.query.QueryText.*;

import org.basex.query.*;
import org.basex.query.expr.*;
import org.basex.query.iter.*;
import org.basex.query.util.*;
import org.basex.query.value.item.*;
import org.basex.query.value.node.*;
import org.basex.query.value.type.*;
import org.basex.query.value.type.SeqType.Occ;
import org.basex.util.*;

/**
 * For clause.
 *
 * @author BaseX Team 2005-12, BSD License
 * @author Christian Gruen
 */
public final class For extends ForLet {
  /** Positional variable. */
  final Var pos;
  /** Full-text score. */
  final Var score;

  /**
   * Constructor.
   * @param ii input info
   * @param e variable input
   * @param v variable
   */
  public For(final InputInfo ii, final Expr e, final Var v) {
    this(ii, e, v, null, null);
  }

  /**
   * Constructor.
   * @param ii input info
   * @param e variable input
   * @param v variable
   * @param p positional variable
   * @param s score variable
   */
  public For(final InputInfo ii, final Expr e, final Var v, final Var p, final Var s) {
    super(ii, e, v);
    pos = p;
    score = s;
  }

  @Override
  public For compile(final QueryContext ctx) throws QueryException {
    expr = expr.compile(ctx);
    type = expr.type();
    size = expr.size();
    if(ctx.grouping) {
      var.ret = SeqType.get(type.type, Occ.ZERO_MORE);
    } else {
      var.size = Math.min(1, size);
      var.ret = type.type.seqType();
    }

    ctx.vars.add(var);
    if(pos   != null) ctx.vars.add(pos);
    if(score != null) ctx.vars.add(score);
    return this;
  }

  @Override
  public Iter iter(final QueryContext ctx) {
    final Var v = var.copy();
    final Var p = pos != null ? pos.copy() : null;
    final Var s = score != null ? score.copy() : null;

    return new Iter() {
      /** Variable stack size. */
      private int vs;
      /** Iterator flag. */
      private Iter ir;
      /** Counter. */
      private int c;

      @Override
      public Item next() throws QueryException {
        init();
        final Item it = ir.next();
        if(it != null) return bind(it, ++c);
        reset();
        return null;
      }

      @Override
      public long size() {
        return expr.size();
      }

      @Override
      public Item get(final long i) throws QueryException {
        init();
        return bind(ir.get(i), i + 1);
      }

      @Override
      public boolean reset() {
        if(ir != null) {
          ctx.vars.size(vs);
          ir.reset();
          ir = null;
          c = 0;
        }
        return true;
      }

      /**
       * Initializes the iterator.
       * @throws QueryException query exception
       */
      private void init() throws QueryException {
        if(ir == null) {
          vs = ctx.vars.size();
          ir = ctx.iter(expr);
          ctx.vars.add(v);
          if(p != null) ctx.vars.add(p);
          if(s != null) ctx.vars.add(s);
        }
      }

      /**
       * Binds an item to the loop variables.
       * @param it item
       * @param i position counter
       * @return specified item
       * @throws QueryException query exception
       */
      private Item bind(final Item it, final long i) throws QueryException {
        v.bind(it, ctx);
        if(p != null) p.bind(Int.get(i), ctx);
        if(s != null) s.bind(Dbl.get(it.score()), ctx);
        return it;
      }
    };
  }

  @Override
  boolean simple(final boolean one) {
    return pos == null && score == null && (!one || type.one() || size == 1);
  }

  @Override
  public void plan(final FElem plan) {
    addPlan(plan, planElem(VAR, var, POS, pos, SCORE, score), expr);
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder(FOR + ' ' + var + ' ');
    if(pos != null) sb.append(AT + ' ' + pos + ' ');
    if(score != null) sb.append(SCORE + ' ' + score + ' ');
    return sb.append(IN + ' ' + expr).toString();
  }

  @Override
  public boolean declares(final Var v) {
    return var.is(v) || pos != null && pos.is(v)
        || score != null && score.is(v);
  }

  @Override
  public Var[] vars() {
    if(pos != null) {
      if(score != null) return new Var[]{ var, pos, score };
      return new Var[]{ var, pos };
    }
    if(score != null) return new Var[]{ var, score };
    return new Var[]{ var };
  }
}
