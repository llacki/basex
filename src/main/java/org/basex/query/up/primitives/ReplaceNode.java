package org.basex.query.up.primitives;

import static org.basex.query.util.Err.*;

import org.basex.data.*;
import org.basex.query.*;
import org.basex.query.iter.*;
import org.basex.query.up.*;
import org.basex.util.*;

/**
 * Replace node primitive.
 *
 * @author BaseX Team 2005-12, BSD License
 * @author Lukas Kircher
 */
public final class ReplaceNode extends NodeCopy {
  /**
   * Constructor.
   * @param p pre
   * @param d data
   * @param i input info
   * @param c node copy
   */
  public ReplaceNode(final int p, final Data d, final InputInfo i,
      final NodeSeqBuilder c) {
    super(PrimitiveType.REPLACENODE, p, d, i, c);
  }

  @Override
  public void apply() {
    final int kind = data.kind(pre);
    final int par = data.parent(pre, kind);
    shifts = data.size(pre, kind) - md.meta.size;

    if(kind == Data.TEXT && md.meta.size == 1 && md.kind(0) == Data.TEXT) {
      // overwrite existing text node
      data.update(pre, Data.TEXT, md.text(0, true));
    } else {
      if(data.nspaces.size() == 0 && md.nspaces.size() == 0) {
        // replaces table nodes directly if no namespaces are specified
        data.replace(pre, md);
      } else {
        data.delete(pre);
        if(kind == Data.ATTR) data.insertAttr(pre, par, md);
        else data.insert(pre, par, md);
      }
    }
  }

  @Override
  public void update(final NamePool pool) {
    if(md == null) return;
    add(pool);
    pool.remove(targetNode());
  }

  @Override
  public boolean adjacentTexts(final int c) {
    final int p = pre + c;
    boolean merged = mergeTexts(data, p - 1, p);
    merged |= mergeTexts(data, p + md.meta.size - 1, p + md.meta.size);

    return merged;
  }

  @Override
  public void merge(final UpdatePrimitive p) throws QueryException {
    UPMULTREPL.thrw(info, targetNode());
  }
}
