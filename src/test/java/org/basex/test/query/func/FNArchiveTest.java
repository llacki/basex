package org.basex.test.query.func;

import static org.basex.query.func.Function.*;

import org.basex.query.util.*;
import org.basex.test.query.*;
import org.junit.*;

/**
 * This class tests the functions of the archive module.
 *
 * @author BaseX Team 2005-12, BSD License
 * @author Christian Gruen
 */
public final class FNArchiveTest extends AdvancedQueryTest {
  /** Test ZIP file. */
  private static final String ZIP = "src/test/resources/xml.zip";

  /**
   * Test method for the archive:create() function.
   */
  @Test
  public void archiveCreate() {
    check(_ARCHIVE_CREATE);
    // simple zip files
    query(COUNT.args(_ARCHIVE_CREATE.args("<entry>X</entry>", "")), "1");
    query(COUNT.args(_ARCHIVE_CREATE.args("<entry level='9'>X</entry>", "")), "1");
    query(COUNT.args(_ARCHIVE_CREATE.args("<entry encoding='US-ASCII'>X</entry>", "")),
        "1");
    query(COUNT.args(_ARCHIVE_CREATE.args(
        "<entry last-modified='2000-01-01T12:12:12'>X</entry>", "")), "1");
    query(COUNT.args(_ARCHIVE_CREATE.args("<entry>X</entry>", "",
        " map { }")), "1");
    query(COUNT.args(_ARCHIVE_CREATE.args("<entry>X</entry>", "",
        " map { 'format':='zip', 'algorithm':='deflate' }")), "1");
    query(COUNT.args(_ARCHIVE_CREATE.args("<entry>X</entry>", "",
        "<archive:options/>")), "1");
    query(COUNT.args(_ARCHIVE_CREATE.args("<entry>X</entry>", "",
        "<archive:options><archive:format value='zip'/>" +
        "<archive:algorithm value='deflate'/></archive:options>")), "1");

    // different number of entries and contents
    error(_ARCHIVE_CREATE.args("<entry>X</entry>", "()"), Err.ARCH_DIFF);
    // name must not be empty
    error(_ARCHIVE_CREATE.args("<entry/>", ""), Err.ARCH_NAME);
    // invalid compression level
    error(_ARCHIVE_CREATE.args("<entry compression-level='x'>X</entry>", ""),
        Err.ARCH_LEVEL);
    error(_ARCHIVE_CREATE.args("<entry compression-level='10'>X</entry>", ""),
        Err.ARCH_LEVEL);
    // invalid modification date
    error(_ARCHIVE_CREATE.args("<entry last-modified='2020'>X</entry>", ""),
        Err.ARCH_MODIFIED);
    // content must be string or base64Binary
    error(_ARCHIVE_CREATE.args("<entry>X</entry>", " 123"), Err.ARCH_STRB64);
    // wrong encoding
    error(_ARCHIVE_CREATE.args("<entry encoding='x'>X</entry>", ""), Err.ARCH_ENCODING);
    // errors while converting a string
    error(_ARCHIVE_CREATE.args("<entry encoding='US-ASCII'>X</entry>", "\u00fc"),
        Err.ARCH_ENCODE);
    error(COUNT.args(_ARCHIVE_CREATE.args("<entry>X</entry>", "",
        " map { 'format':='rar' }")), Err.ARCH_SUPP);
    error(COUNT.args(_ARCHIVE_CREATE.args("<entry>X</entry>", "",
        "<archive:options><archive:format value='rar'/></archive:options>")),
        Err.ARCH_SUPP);
  }

  /**
   * Test method for the archive:entries() function.
   */
  @Test
  public void archiveEntries() {
    check(_ARCHIVE_ENTRIES);
    // read entries
    query(COUNT.args(_ARCHIVE_ENTRIES.args(_FILE_READ_BINARY.args(ZIP))), "5");
    // simple zip files
    query(COUNT.args(_ARCHIVE_ENTRIES.args(_FILE_READ_BINARY.args(ZIP)) +
        "[@size][@last-modified][@compressed-size]"), "5");
  }

  /**
   * Test method for the archive:extract-texts() function.
   */
  @Test
  public void archiveExtractTexts() {
    check(_ARCHIVE_EXTRACT_TEXT);
    // extract all entries
    query(COUNT.args(_ARCHIVE_EXTRACT_TEXT.args(_FILE_READ_BINARY.args(ZIP))), "5");
    // extract all entries
    query("let $a := " + _FILE_READ_BINARY.args(ZIP) +
          "let $b := " + _ARCHIVE_ENTRIES.args("$a") +
          "return " + COUNT.args(_ARCHIVE_EXTRACT_TEXT.args("$a", "$b")), 5);
    // extract single entry
    query("let $a := " + _FILE_READ_BINARY.args(ZIP) +
          "let $b := " + _ARCHIVE_EXTRACT_TEXT.args("$a", "test/input.xml") +
          "let $c := " + PARSE_XML.args("$b") +
          "return $c//title/text()", "XML");
  }

  /**
   * Test method for the archive:extract-binaries() function.
   */
  @Test
  public void archiveExtractBinary() {
    check(_ARCHIVE_EXTRACT_BINARY);
    // extract all entries
    query(COUNT.args(_ARCHIVE_EXTRACT_BINARY.args(_FILE_READ_BINARY.args(ZIP))), "5");
    // extract all entries
    query("let $a := " + _FILE_READ_BINARY.args(ZIP) +
          "let $b := " + _ARCHIVE_ENTRIES.args("$a") +
          "return count(" + _ARCHIVE_EXTRACT_BINARY.args("$a", "$b") + ")", 5);
    // extract single entry
    query("let $a := " + _FILE_READ_BINARY.args(ZIP) +
          "let $b := " + _ARCHIVE_EXTRACT_BINARY.args("$a", "test/input.xml") +
          "let $c := " + _CONVERT_BINARY_TO_STRING.args("$b") +
          "let $d := " + PARSE_XML.args("$c") +
          "return $d//title/text()", "XML");
  }

  /**
   * Test method for the archive:update() function.
   */
  @Test
  public void archiveUpdate() {
    check(_ARCHIVE_UPDATE);
    // add a new entry
    query(_FILE_READ_BINARY.args(ZIP) + " ! " +
        _ARCHIVE_UPDATE.args(" .", "<entry>X</entry>", "X") + " ! " +
        COUNT.args(_ARCHIVE_ENTRIES.args(" .")), 6);
    query(_ARCHIVE_CREATE.args("<entry>X</entry>", "X") + " ! " +
        _ARCHIVE_UPDATE.args(" .", "<entry>Y</entry>", "Y") + " ! " +
        _ARCHIVE_EXTRACT_TEXT.args(" ."), "X Y");
    // updates an existing entry
    query(_ARCHIVE_CREATE.args("<entry>X</entry>", "X") + " ! " +
        _ARCHIVE_UPDATE.args(" .", "<entry>X</entry>", "Y") + " ! " +
        _ARCHIVE_EXTRACT_TEXT.args(" ."), "Y");
  }

  /**
   * Test method for the archive:delete() function.
   */
  @Test
  public void archiveDelete() {
    check(_ARCHIVE_DELETE);
    // delete single entry
    query("let $a := " + _FILE_READ_BINARY.args(ZIP) +
          "let $b := " + _ARCHIVE_DELETE.args("$a", "infos/stopWords") +
          "let $c := " + _ARCHIVE_ENTRIES.args("$b") +
          "return count($c)", 4);
    // delete all entries except for the first
    query("let $a := " + _FILE_READ_BINARY.args(ZIP) +
          "let $b := " + _ARCHIVE_ENTRIES.args("$a") +
          "let $c := " + _ARCHIVE_DELETE.args("$a", "($b/text())[position() > 1]") +
          "return count(archive:entries($c))", "1");
  }
}
