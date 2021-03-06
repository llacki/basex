package org.basex.core.cmd;

import org.basex.core.*;
import org.basex.io.*;

/**
 * Evaluates the 'checks' command: opens an existing database or
 * creates a new one.
 *
 * @author BaseX Team 2005-12, BSD License
 * @author Christian Gruen
 */
public final class Check extends Command {
  /**
   * Default constructor.
   * @param path file path
   */
  public Check(final String path) {
    super(Perm.NONE, path);
  }

  @Override
  protected boolean run() {
    // close existing database
    new Close().run(context);

    // get path and database name
    final QueryInput input = new QueryInput(args[0]);
    input.db = input.io.dbname();

    // choose OPEN if user has no create permissions, or if database exists
    final boolean create = context.user.has(Perm.CREATE);
    final Command cmd = !create || mprop.dbpath(input.db).exists() ?
      new Open(input.db) : new CreateDB(input.db, input.original);

    // execute command
    final boolean ok = cmd.run(context);
    final String msg = cmd.info().trim();
    return ok ? info(msg) : error(msg);
  }
}
