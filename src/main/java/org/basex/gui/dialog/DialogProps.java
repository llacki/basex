package org.basex.gui.dialog;

import static org.basex.core.Text.*;
import static org.basex.gui.GUIConstants.*;

import java.awt.*;

import javax.swing.event.*;

import org.basex.core.*;
import org.basex.core.cmd.*;
import org.basex.data.*;
import org.basex.gui.*;
import org.basex.gui.layout.*;
import org.basex.index.*;
import org.basex.util.*;
import org.basex.util.list.*;

/**
 * Database properties dialog.
 *
 * @author BaseX Team 2005-12, BSD License
 * @author Christian Gruen
 */
public final class DialogProps extends BaseXDialog {
  /** Index types. */
  static final String[] HELP = {
    "", "", H_PATH_INDEX, H_TEXT_INDEX, H_ATTR_INDEX, ""
  };
  /** Index types. */
  static final IndexType[] TYPES = {
    IndexType.TAG, IndexType.ATTNAME, IndexType.PATH,
    IndexType.TEXT, IndexType.ATTRIBUTE, IndexType.FULLTEXT
  };
  /** Label strings. */
  private static final String[] LABELS = {
    ELEMENTS, ATTRIBUTES, PATH_INDEX, TEXT_INDEX, ATTRIBUTE_INDEX, FULLTEXT_INDEX
  };

  /** Full-text tab. */
  final BaseXBack tabFT;
  /** Name tab. */
  final BaseXBack tabNames;
  /** Name tab. */
  final BaseXBack tabPath;
  /** Name tab. */
  final BaseXBack tabValues;
  /** Contains the panels that are currently being updated. */
  final IntList updated = new IntList();
  /** Tabbed pane. */
  final BaseXTabs tabs;
  /** Resource panel. */
  final DialogResources resources;
  /** Add panel. */
  final DialogAdd add;
  /** Index information. */
  final BaseXEditor[] infos = new BaseXEditor[LABELS.length];

  /** Index labels. */
  private final BaseXLabel[] labels = new BaseXLabel[LABELS.length];
  /** Index buttons. */
  private final BaseXButton[] indxs = new BaseXButton[LABELS.length];
  /** Index panels. */
  private final BaseXBack[] panels = new BaseXBack[LABELS.length];
  /** Editable full-text options. */
  private final DialogFT ft;

  /**
   * Default constructor.
   * @param main reference to the main window
   */
  public DialogProps(final GUI main) {
    super(main, DB_PROPS);
    main.setCursor(CURSORWAIT);

    panel.setLayout(new BorderLayout(5, 0));

    // resource tree
    resources = new DialogResources(this);

    // tab: resources
    add = new DialogAdd(this);
    ft = new DialogFT(this, false);
    final BaseXBack tabRes = add.border(8);

    final Data data = main.context.data();
    for(int i = 0; i < LABELS.length; ++i) {
      labels[i] = new BaseXLabel(LABELS[i]).large();
      panels[i] = new BaseXBack(new BorderLayout(0, 4));
      infos[i] = new BaseXEditor(false, this);
      infos[i].setText(Token.token(PLEASE_WAIT_D));
      BaseXLayout.setHeight(infos[i], 200);
      if(i != 1) {
        indxs[i] = new BaseXButton(" ", this);
        indxs[i].setEnabled(!data.inMemory());
      }
    }

    // tab: name indexes
    tabNames = new BaseXBack(new GridLayout(2, 1, 0, 8)).border(8);
    add(0, tabNames, null);
    add(1, tabNames, null);

    // tab: path index
    tabPath = new BaseXBack(new GridLayout(1, 1)).border(8);
    add(2, tabPath, null);

    // tab: value indexes
    tabValues = new BaseXBack(new GridLayout(2, 1, 0, 8)).border(8);
    add(3, tabValues, null);
    add(4, tabValues, null);

    // tab: full-text index
    tabFT = new BaseXBack(new GridLayout(1, 1)).border(8);
    add(5, tabFT, null);

    // tab: database info
    final BaseXBack tabGeneral = new BaseXBack(new BorderLayout(0, 8)).border(8);
    final Font f = tabGeneral.getFont();
    final BaseXLabel doc = new BaseXLabel(data.meta.name).border(0, 0, 6, 0).large();
    BaseXLayout.setWidth(doc, 400);
    tabGeneral.add(doc, BorderLayout.NORTH);

    final String db = InfoDB.db(data.meta, true, false, true);
    final TokenBuilder info = new TokenBuilder(db);
    if(data.nspaces.size() != 0) {
      info.bold().add(NL + NAMESPACES + NL).norm().add(data.nspaces.info());
    }

    final BaseXEditor text = text(info.finish());
    text.setFont(f);
    tabGeneral.add(text, BorderLayout.CENTER);

    tabs = new BaseXTabs(this);
    tabs.addTab(RESOURCES, tabRes);
    tabs.addTab(NAMES, tabNames);
    tabs.addTab(PATH_INDEX, tabPath);
    tabs.addTab(INDEXES, tabValues);
    tabs.addTab(FULLTEXT, tabFT);
    tabs.addTab(GENERAL, tabGeneral);

    tabs.addChangeListener(new ChangeListener() {
      @Override
      public synchronized void stateChanged(final ChangeEvent evt) {
        updateInfo();
      }
    });

    set(resources, BorderLayout.WEST);
    set(tabs, BorderLayout.CENTER);

    action(this);
    setResizable(true);
    setMinimumSize(getPreferredSize());

    main.setCursor(CURSORARROW);
    finish(null);
  }

  /**
   * Updates the currently visible index panel.
   */
  synchronized void updateInfo() {
    final Object o = tabs.getSelectedComponent();
    final IntList il = new IntList();
    if(o == tabNames) {
      il.add(0);
      il.add(1);
    } else if(o == tabPath) {
      il.add(2);
    } else if(o == tabValues) {
      il.add(3);
      il.add(4);
    } else if(o == tabFT) {
      il.add(5);
    }

    final Data data = gui.context.data();
    final boolean[] val = { true, true, true, data.meta.textindex,
        data.meta.attrindex, data.meta.ftxtindex };
    for(int i = 0; i < il.size(); i++) {
      final int idx = il.get(i);
      if(updated.contains(idx)) continue;
      updated.add(idx);
      new Thread() {
        @Override
        public void run() {
          infos[idx].setText(val[idx] ? data.info(TYPES[idx]) : Token.token(HELP[idx]));
          updated.delete(idx);
        };
      }.start();
    }
  }

  /**
   * Adds index information to the specified panel and tab.
   * @param p index offset
   * @param tab panel tab
   * @param info optional info to display
   */
  private void add(final int p, final BaseXBack tab, final BaseXBack info) {
    final BaseXBack idx = new BaseXBack(new BorderLayout(8, 0));
    idx.add(labels[p], BorderLayout.WEST);
    if(indxs[p] != null) idx.add(indxs[p], BorderLayout.EAST);
    panels[p].add(idx, BorderLayout.NORTH);
    panels[p].add(info != null ? info : infos[p], BorderLayout.CENTER);
    tab.add(panels[p]);
  }

  /**
   * Returns a text box.
   * @param txt contents
   * @return text box
   */
  private BaseXEditor text(final byte[] txt) {
    final BaseXEditor text = new BaseXEditor(false, this);
    text.setText(txt);
    BaseXLayout.setHeight(text, 200);
    return text;
  }

  @Override
  public void action(final Object cmp) {
    if(cmp != null) {
      for(int i = 0; i < LABELS.length; i++) {
        if(cmp != indxs[i]) continue;
        final String label = indxs[i].getText();
        final Command cmd;
        if(label.equals(OPTIMIZE + DOTS)) {
          cmd = new Optimize();
        } else if(label.equals(DROP + DOTS)) {
          cmd = new DropIndex(TYPES[i]);
        } else {
          cmd = new CreateIndex(TYPES[i]);
          ft.setOptions();
        }
        infos[i].setText(Token.token(PLEASE_WAIT_D));
        DialogProgress.execute(this, "", cmd);
        return;
      }
    }

    resources.action(cmp);
    add.action(cmp);

    final Data data = gui.context.data();
    final boolean[] val = {
      true, true, true, data.meta.textindex, data.meta.attrindex, data.meta.ftxtindex
    };

    if(cmp == this) {
      final boolean utd = data.meta.uptodate;
      for(int i = 0; i < LABELS.length; ++i) {
        // structural index/statistics?
        final boolean struct = i < 3;
        String lbl = LABELS[i];
        if(struct && !utd) lbl += " (" + OUT_OF_DATE + ')';
        // updates labels and infos
        labels[i].setText(lbl);
        // update button (label, disable/enable)
        if(indxs[i] != null) {
          indxs[i].setText((struct ? OPTIMIZE : val[i] ? DROP : CREATE) + DOTS);
          if(struct) indxs[i].setEnabled(!utd);
        }
      }
      // full-text options
      final int f = 5;
      tabFT.removeAll();
      panels[f].removeAll();
      add(f, tabFT, val[f] ? null : ft);
      panels[f].revalidate();
      panels[f].repaint();
      updateInfo();
    }

    ft.action(true);
  }

  @Override
  public void close() {
    super.close();
    ft.setOptions();
  }
}
