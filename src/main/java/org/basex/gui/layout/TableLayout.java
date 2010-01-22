package org.basex.gui.layout;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;

/**
 * This LayoutManager is similar to the GridLayout. The added components
 * keep their minimum size even when the parent container is resized.
 *
 * @author Workgroup DBIS, University of Konstanz 2005-10, ISC License
 * @author Christian Gruen
 */
public final class TableLayout implements LayoutManager {
  /** Number of columns. */
  private final int cols;
  /** Number of rows. */
  private final int rows;
  /** Horizontal inset. */
  private final int insetX;
  /** Vertical inset. */
  private final int insetY;
  /** Panel width. */
  private int width;
  /** Panel height. */
  private int height;
  /** Horizontal position. */
  private final int[] posX;
  /** Vertical position. */
  private final int[] posY;

  /**
   * Creates a grid layout with the specified number of rows and columns.
   * When displayed, the grid has the minimum size.
   * @param r number of rows
   * @param c number of columns
   */
  public TableLayout(final int r, final int c) {
    this(r, c, 0, 0);
  }

  /**
   * Creates a grid layout with the specified number of rows and columns.
   * When displayed, the grid has the minimum size.
   * @param r number of rows
   * @param c number of columns
   * @param ix horizontal inset size
   * @param iy vertical inset size
   */
  public TableLayout(final int r, final int c, final int ix, final int iy) {
    rows = r;
    cols = c;
    insetX = ix;
    insetY = iy;
    posX = new int[c];
    posY = new int[r];
  }

  /**
   * Adds the specified component with the specified name to the layout.
   * @param name the component name
   * @param comp the component to be added
   */
  public void addLayoutComponent(final String name, final Component comp) { }

  /**
   * Removes the specified component from the layout.
   * @param comp the component to be removed
   */
  public void removeLayoutComponent(final Component comp) { }

  /**
   * Determines the preferred size of the container argument using this grid
   * layout.
   * @param parent the layout container
   * @return the preferred dimensions for painting the container
   */
  public Dimension preferredLayoutSize(final Container parent) {
    synchronized(parent.getTreeLock()) {
      final Insets in = parent.getInsets();
      final int nr = parent.getComponentCount();

      int maxW = 0;
      int maxH = 0;
      for(int i = 0; i < cols; i++) {
        posX[i] = maxW;
        final int w = maxW;
        int h = 0;

        for(int j = 0; j < rows; j++) {
          final int n = j * cols + i;
          if(n >= nr) break;

          final Component c = parent.getComponent(n);
          final Dimension d = c.getPreferredSize();
          if(maxW < w + d.width) maxW = w + d.width;
          if(posY[j] < h) posY[j] = h;
          else h = posY[j];
          h += d.height;
        }
        if(maxH < h) maxH = h;
      }
      width = in.left + maxW + (cols - 1) * insetX + in.right;
      height = in.top + maxH + (rows - 1) * insetY + in.bottom;

      return new Dimension(width, height);
    }
  }

  /**
   * Determines the minimum size of the container argument using this grid
   * layout.
   * @param parent the layout container
   * @return the preferred dimensions for painting the container
   */
  public Dimension minimumLayoutSize(final Container parent) {
    return preferredLayoutSize(parent);
  }

  /**
   * Lays out the specified container using this layout.
   * @param p the layout container
   */
  public void layoutContainer(final Container p) {
    preferredLayoutSize(p);
    synchronized(p.getTreeLock()) {
      final Insets in = p.getInsets();
      final int nr = p.getComponentCount();
      for(int j = 0; j < rows; j++) {
        for(int i = 0; i < cols; i++) {
          final int n = j * cols + i;
          if(n >= nr) return;
          final Dimension cs = p.getComponent(n).getPreferredSize();
          final int x = in.left + posX[i] + i * insetX;
          final int y = in.top + posY[j] + j * insetY;
          final int w = cs.width > 0 ? cs.width : width - in.left - in.right;
          final int h = cs.height > 0 ? cs.height : height - in.top - in.bottom;
          p.getComponent(n).setBounds(x, y, w, h);
        }
      }
    }
  }
}