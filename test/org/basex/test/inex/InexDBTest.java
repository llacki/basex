package org.basex.test.inex;

import static org.basex.core.Text.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.basex.core.Context;
import org.basex.core.Main;
import org.basex.core.Prop;
import org.basex.core.proc.Close;
import org.basex.core.proc.List;
import org.basex.core.proc.Open;
import org.basex.core.proc.Set;
import org.basex.core.proc.XQuery;
import org.basex.server.ClientSession;
import org.basex.io.CachedOutput;
import org.basex.io.PrintOutput;
import org.basex.util.Args;
import org.basex.util.Performance;
import org.basex.util.StringList;

/**
 * Simple INEX Database test.
 *
 * @author Workgroup DBIS, University of Konstanz 2005-09, ISC License
 * @author Christian Gruen
 * @author Sebastian Gath
 */
public final class InexDBTest {
  /** Submission file. */
  static final String SUBMISSION = "submission.xml";
  /** Updated submission file. */
  static final String SUBMISSIONU = "submissionU.xml";
  /** Topics file. */
  static final String TOPICS = "topics.xml";
  /** Query file. */
  static final String QUERIES = "inex.queries";
  /** Times output file. */
  static final String TIMES = "times";
  /** Database prefix. */
  static final String DBPREFIX = "inex";

  /** Database context. */
  private final Context ctx = new Context();
  /** Session. */
  private ClientSession session;
  /** Queries. */
  private StringList queries;
  /** Databases. */
  private StringList databases;

  /** Maximum number of databases. */
  private int dbindex = -1;
  /** Maximum number of queries. */
  private int quindex = -1;
  /** Number of runs. */
  private int runs = 1;
  /** Shows process info. */
  private boolean info;
  /** Container for qtimes and results. */
  private PrintOutput res;
  /** Budget for a query in ms. */
  private double  budget = -1;
  /** Remaining query time - used for budget queries.  */
  private double[] rqt;

  /**
   * Main test method.
   * @param args command-line arguments
   * @throws Exception exception
   */
  public static void main(final String[] args) throws Exception {
    new InexDBTest(args);
  }

  /**
   * Default constructor.
   * @param args command-line arguments
   * @throws Exception exception
   */
  private InexDBTest(final String[] args) throws Exception {
    final Performance p = new Performance();
    Main.outln(Main.name(InexDBTest.class));

    if(!parseArguments(args)) return;

    // cache queries
    final BufferedReader br = new BufferedReader(new FileReader(QUERIES));
    queries = new StringList();
    String l;
    int c = 0;
    while((l = br.readLine()) != null) {
      if(quindex == -1 || ++c == quindex) {
        queries.add(l.substring(l.lastIndexOf(';') + 1));
      }
    }
    br.close();
    rqt = new double[queries.size()];

    // cache database names
    databases = new StringList();
    for(final String s : List.list(ctx)) {
      if(s.startsWith(DBPREFIX) && (dbindex == -1 ||
         s.equals(DBPREFIX + dbindex))) databases.add(s);
    }

    Main.outln("=> % queries on % databases, % runs: time in ms\n",
        queries.size(), databases.size(), runs);

    // run test
    if(dbindex == -1 && quindex == -1) res = new PrintOutput(TIMES);
    test();
    if(res != null) res.close();

    Main.outln("Total Time: " + p);
  }

  /**
   * Second test, opening each databases before running the queries.
   * @throws Exception exception
   */
  private void test() throws Exception {
    // loop through all databases
    for(int d = 0; d < databases.size(); d++) {
      // open database and loop through all queries
      session.execute(new Open(databases.get(d)));
      for(int q = 0; q < queries.size(); q++) {
        for(int r = 0; r < runs; r++) query(d, q, r == runs - 1);
      }
      session.execute(new Close());
    }
  }

  /**
   * Performs a single query.
   * @param db database offset
   * @param qu query offset
   * @param store store flag
   * @throws Exception exception
   */
  private void query(final int db, final int qu, final boolean store)
      throws Exception {

    if(budget > -1) {
      final double timer = budget - rqt[qu];
      if(timer <= 0) {
        if(store) {
          if(res != null) res.println(0 + ";" + 0);
          Main.outln("Query % on %: %", qu + 1, databases.get(db), 0);
        }
        return;
      }
      session.execute(new Set(Prop.IBT, timer));
    }

    session.execute(new Set(Prop.SERIALIZE, false));
    if(session.execute(new XQuery(queries.get(qu)))) {
      session.output(new CachedOutput());
      if(!store) return;

      final String str = session.info();
      final String time = find(str, "Total Time: (.*) ms");
      final String items = find(str, "Results   : ([0-9]+) Item");
      
      // output result
      Main.outln("Query % on %: % (% items)",
          qu + 1, databases.get(db), time, items);

      if(info) Main.outln("- " + find(str, "Result: (.*)\\r?\\n"));

      rqt[qu] += Double.parseDouble(time);
      if(res != null) res.println(time + ";" + items);
    } else {
      Main.outln(session.info());
      if(res != null) res.println("-1;-1");
    }
  }

  /**
   * Finds a string in the specified pattern.
   * @param str input string
   * @param pat regular pattern
   * @return resulting string
   */
  private String find(final String str, final String pat) {
    final Matcher m = Pattern.compile(pat).matcher(str);
    return m.find() ? m.group(1) : "";
  }

  /**
   * Parses the command-line arguments.
   * @param args the command-line arguments
   * @return true if all arguments have been correctly parsed
   */
  private boolean parseArguments(final String[] args) {
    final Args arg = new Args(args);
    boolean ok = true;
    try {
      while(arg.more() && ok) {
        if(arg.dash()) {
          final char c = arg.next();
          if(c == 'd') {
            dbindex = arg.num();
          } else if(c == 'q') {
            quindex = arg.num();
          } else if(c == 'r') {
            runs = arg.num();
          } else if(c == 'v') {
            info = true;
          } else if(c == 'b') {
            budget = arg.num();
          } else {
            ok = false;
          }
        } else {
          ok = false;
        }
      }

      session = new ClientSession(ctx);
      session.execute(new Set(Prop.INFO, true));
      session.execute(new Set(Prop.ALLINFO, info));
    } catch(final Exception ex) {
      ok = false;
      Main.errln("Please run BaseXServer for using server mode.");
      ex.printStackTrace();
    }

    if(!ok) {
      Main.outln("Usage: " + Main.name(this) + " [options]" + NL +
        "  -d<no>  use specified database (0-9)" + NL +
        "  -q<no>  perform specified query (1-#queries)" + NL +
        "  -r<no>  number of runs" + NL +
        "  -v      show process info");
    }
    return ok;
  }
}