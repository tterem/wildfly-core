/*
 * Copyright 2019 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.test.integration.management.cli;

import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 * These tests check highlighting of matching open/close brackets in cli
 *
 * Structure of each test is:
 * - Prepare command
 * - Prepare instructions for cursor movement using CursorMovement.Builder
 * - Push command and instructions to cli
 * - Prepare ANSI sequence describing expected cursor movement and character highlighting using AnsiSequence.Builder
 * - Check cli output for expected ANSI sequence
 *
 * @author Tomas Terem tterem@redhat.com
 */
@RunWith(WildflyTestRunner.class)
public class BracketsHighlightingTestCase {

   private static CliProcessWrapper cli;
   private static String hostAndPort = TestSuiteEnvironment.getServerAddress() + ":" + TestSuiteEnvironment.getServerPort();

   /*
    * Usually, when moving a cursor, relative numbers are not used, it rather
    * move to the start of the line and then move back to the desired position
    * For example: 2 to the left == 50 to the left, and then 48 to the right
    * Host address is part of the line and its length can vary, which is affecting these hardcoded numbers
    * This attribute adjust this difference
    */
   private static int d = hostAndPort.length() - "127.0.0.1:9990".length();

   /**
    * Initialize CommandContext before all tests
    */
   @BeforeClass
   public static void init() {
      cli = new CliProcessWrapper()
            .addCliArgument("--connect")
            .addCliArgument("--controller=" + hostAndPort);
      cli.executeInteractive();
   }

   @Before
   public void clearOutput() {
      cli.clearOutput();
   }

   /**
    * Terminate CommandContext after all tests are executed
    */
   @AfterClass
   public static void close() {
      cli.destroyProcess();
   }

   /**
    * Write expression '()' and move cursor 1 to the left
    * Cursor will be on ')', so '(' will be highlighted
    * Then highlighting will be removed and cursor will be moved to the end of the line
    * @throws Exception
    */
   @Test
   public void testBasic() throws Exception {
      // prepare cli command
      String command = "()";

      // prepare instructions to move cursor
      CursorMovement cursorMovement = new CursorMovement.Builder()
            .left(1)
            .build();

      // execute cli
      cli.pushLineAndWaitForResults(command + cursorMovement);

      // get output
      String out = cli.getOutput();

      // prepare expected ansi sequence
      AnsiSequence expectedSequence = new AnsiSequence.Builder()
            .left(1)
            .saveCursor()
            .left(31 + d)
            .right(30 + d)
            .highlight('(')
            .left(1)
            .restoreCursor()
            .right(1)
            .saveCursor()
            .left(32 + d)
            .right(30 + d)
            .undoHighlight('(')
            .left(1)
            .restoreCursor()
            .build();

      // check if expected sequence is present in output
      Assert.assertTrue(out.contains(expectedSequence.toString()));
   }

   /**
    * Write expression '()' and move cursor 2 to the left
    * First cursor will be on ')', so '(' will be highlighted
    * Then it moves to '(', so its highlighting will be removed and ')' will be highlighted
    * Then ')' highlighting will be removed as well and cursor will be moved to the end of the line
    * @throws Exception
    */
   @Test
   public void testBasicTwoLeft() throws Exception {
      String command = "()";
      CursorMovement cursorMovement = new CursorMovement.Builder()
            .left(2)
            .build();

      cli.pushLineAndWaitForResults(command + cursorMovement);
      String out = cli.getOutput();

      AnsiSequence expectedSequence = new AnsiSequence.Builder()
            .left(1)
            .saveCursor()
            .left(31 + d)
            .right(30 + d)
            .highlight('(')
            .left(1)
            .restoreCursor()
            .left(1)
            .saveCursor()
            .left(30 + d)
            .right(30 + d)
            .undoHighlight('(')
            .left(1)
            .restoreCursor()
            .saveCursor()
            .left(30 + d)
            .right(31 + d)
            .highlight(')')
            .left(1)
            .restoreCursor()
            .right(2)
            .saveCursor()
            .left(32 + d)
            .right(31 + d)
            .undoHighlight(')')
            .left(1)
            .restoreCursor()
            .build();

      Assert.assertTrue(out.contains(expectedSequence.toString()));
   }

   /**
    * Write expression '([){]}' and move cursor through the whole expression to the left and then back to the right
    * Check output for expected ANSI sequence describing cursor movement and highlighting
    * @throws Exception
    */
   @Test
   public void testLeftAndRightCorrectMixedUpExpression() throws Exception {
      String command = "([){]}";
      CursorMovement cursorMovement = new CursorMovement.Builder()
            .left(6)
            .right(6)
            .build();

      cli.pushLineAndWaitForResults(command + cursorMovement);
      String out = cli.getOutput();

      AnsiSequence expectedSequence = new AnsiSequence.Builder()                                      // ([){]}_
            .left(1).saveCursor().left(35 + d).right(33 + d).highlight('{')                           // ([)*{*]_}
            .leftAndRestore().left(1).saveCursor().left(34 + d).right(33 + d).undoHighlight('{')      // ([){_]}
            .leftAndRestore().saveCursor().left(34 + d).right(31 + d).highlight('[')                  // (*[*){_]}
            .leftAndRestore().left(1).saveCursor().left(33 + d).right(31 + d).undoHighlight('[')      // ([)_{]}
            .leftAndRestore().saveCursor().left(33 + d).right(35 + d).highlight('}')                  // ([)_{]*}*
            .leftAndRestore().left(1).saveCursor().left(32 + d).right(35 + d).undoHighlight('}')      // ([_){]}
            .leftAndRestore().saveCursor().left(32 + d).right(30 + d).highlight('(')                  // *(*[_){]}
            .leftAndRestore().left(1).saveCursor().left(31 + d).right(30 + d).undoHighlight('(')      // (_[){]}
            .leftAndRestore().saveCursor().left(31 + d).right(34 + d).highlight(']')                  // (_[){*]*}
            .leftAndRestore().left(1).saveCursor().left(30 + d).right(34 + d).undoHighlight(']')      // _([){]}
            .leftAndRestore().saveCursor().left(30 + d).right(32 + d).highlight(')')                  // _([*)*{]}
            .leftAndRestore().right(1).saveCursor().left(31 + d).right(32 + d).undoHighlight(')')     // (_[){]}
            .leftAndRestore().saveCursor().left(31 + d).right(34 + d).highlight(']')                  // (_[){*]*}
            .leftAndRestore().right(1).saveCursor().left(32 + d).right(34 + d).undoHighlight(']')     // ([_){]}
            .leftAndRestore().saveCursor().left(32 + d).right(30 + d).highlight('(')                  // *(*[_){]}
            .leftAndRestore().right(1).saveCursor().left(33 + d).right(30 + d).undoHighlight('(')     // ([)_{]}
            .leftAndRestore().saveCursor().left(33 + d).right(35 + d).highlight('}')                  // ([)_{]*}*
            .leftAndRestore().right(1).saveCursor().left(34 + d).right(35 + d).undoHighlight('}')     // ([){_]}
            .leftAndRestore().saveCursor().left(34 + d).right(31 + d).highlight('[')                  // (*[*){_]}
            .leftAndRestore().right(1).saveCursor().left(35 + d).right(31 + d).undoHighlight('[')     // ([){]_}
            .leftAndRestore().saveCursor().left(35 + d).right(33 + d).highlight('{')                  // ([)*{*]_}
            .leftAndRestore().right(1).saveCursor().left(36 + d).right(33 + d).undoHighlight('{')     // ([){]}_
            .leftAndRestore()
            .build();

      Assert.assertTrue(out.contains(expectedSequence.toString()));
   }

   /**
    * Write '/abc:add({a=[], b=[], c={[],()}})' and go through the whole expression by moving cursor to the left
    * Check output for expected ANSI sequence describing cursor movement and highlighting
    * @throws Exception
    */
   @Test
   public void testWellFormedExpression() throws Exception {
      String command = "/abc:add({a=[], b=[], c={[],()}})";
      CursorMovement cursorMovement = new CursorMovement.Builder()
            .left(33)
            .build();

      cli.pushLineAndWaitForResults(command + cursorMovement);
      String out = cli.getOutput();

      AnsiSequence expectedSequence = new AnsiSequence.Builder()                                                        // /abc:add({a=[], b=[], c={[],()}})_
            .left(1).saveCursor().left(62 + d).right(38 + d).highlight('(')                                             // /abc:add*(*{a=[], b=[], c={[],()}}_)
            .leftAndRestore().left(1).saveCursor().left(61 + d).right(38 + d).undoHighlight('(')                        // /abc:add({a=[], b=[], c={[],()}_})
            .leftAndRestore().saveCursor().left(61 + d).right(39 + d).highlight('{')                                    // /abc:add(*{*a=[], b=[], c={[],()}_})
            .leftAndRestore().left(1).saveCursor().left(60 + d).right(39 + d).undoHighlight('{')                        // /abc:add({a=[], b=[], c={[],()_}})
            .leftAndRestore().saveCursor().left(60 + d).right(54 + d).highlight('{')                                    // /abc:add({a=[], b=[], c=*{*[],()_}})
            .leftAndRestore().left(1).saveCursor().left(59 + d).right(54 + d).undoHighlight('{')                        // /abc:add({a=[], b=[], c={[],(_)}})
            .leftAndRestore().saveCursor().left(59 + d).right(58 + d).highlight('(')                                    // /abc:add({a=[], b=[], c={[],*(*_)}})
            .leftAndRestore().left(1).saveCursor().left(58 + d).right(58 + d).undoHighlight('(')                        // /abc:add({a=[], b=[], c={[],_()}})
            .leftAndRestore().saveCursor().left(58 + d).right(59 + d).highlight(')')                                    // /abc:add({a=[], b=[], c={[],_(*)*}})
            .leftAndRestore().left(1).saveCursor().left(57 + d).right(59 + d).undoHighlight(')')                        // /abc:add({a=[], b=[], c={[]_,()}})
            .leftAndRestore().left(1).saveCursor().left(56 + d).right(55 + d).highlight('[')                            // /abc:add({a=[], b=[], c={*[*_],()}})
            .leftAndRestore().left(1).saveCursor().left(55 + d).right(55 + d).undoHighlight('[')                        // /abc:add({a=[], b=[], c={_[],()}})
            .leftAndRestore().saveCursor().left(55 + d).right(56 + d).highlight(']')                                    // /abc:add({a=[], b=[], c={_[*]*,()}})
            .leftAndRestore().left(1).saveCursor().left(54 + d).right(56 + d).undoHighlight(']')                        // /abc:add({a=[], b=[], c=_{[],()}})
            .leftAndRestore().saveCursor().left(54 + d).right(60 + d).highlight('}')                                    // /abc:add({a=[], b=[], c=_{[],()*}*})
            .leftAndRestore().left(1).saveCursor().left(53 + d).right(60 + d).undoHighlight('}')                        // /abc:add({a=[], b=[], c_={[],()}})
            .leftAndRestore().left(1).left(1).left(1).left(1).saveCursor().left(49 + d).right(48 + d).highlight('[')    // /abc:add({a=[], b=*[*_], c={[],()}})
            .leftAndRestore().left(1).saveCursor().left(48 + d).right(48 + d).undoHighlight('[')                        // /abc:add({a=[], b=_[], c={[],()}})
            .leftAndRestore().saveCursor().left(48 + d).right(49 + d).highlight(']')                                    // /abc:add({a=[], b=_[*]*, c={[],()}})
            .leftAndRestore().left(1).saveCursor().left(47 + d).right(49 + d).undoHighlight(']')                        // /abc:add({a=[], b_=[], c={[],()}})
            .leftAndRestore().left(1).left(1).left(1).left(1).saveCursor().left(43 + d).right(42 + d).highlight('[')    // /abc:add({a=*[*_], b=[], c={[],()}})
            .leftAndRestore().left(1).saveCursor().left(42 + d).right(42 + d).undoHighlight('[')                        // /abc:add({a=_[], b=[], c={[],()}})
            .leftAndRestore().saveCursor().left(42 + d).right(43 + d).highlight(']')                                    // /abc:add({a=_[*]*, b=[], c={[],()}})
            .leftAndRestore().left(1).saveCursor().left(41 + d).right(43 + d).undoHighlight(']')                        // /abc:add({a_=[], b=[], c={[],()}})
            .leftAndRestore().left(1).left(1).saveCursor().left(39 + d).right(61 + d).highlight('}')                    // /abc:add(_{a=[], b=[], c={[],()}*}*)
            .leftAndRestore().left(1).saveCursor().left(38 + d).right(61 + d).undoHighlight('}')                        // /abc:add_({a=[], b=[], c={[],()}})
            .leftAndRestore().saveCursor().left(38 + d).right(62 + d).highlight(')')                                    // /abc:add_({a=[], b=[], c={[],()}}*)*
            .leftAndRestore().left(1).saveCursor().left(37 + d).right(62 + d).undoHighlight(')')                        // /abc:ad_d({a=[], b=[], c={[],()}})
            .leftAndRestore().left(1).left(1).left(1).left(1).left(1).left(1).left(1)                                   //_/abc:add({a=[], b=[], c={[],()}})
            .right(33)                                                                                                  // /abc:add({a=[], b=[], c={[],()}})_
            .build();

      Assert.assertTrue(out.contains(expectedSequence.toString()));
   }


//   @Test
//   public void testNonWellFormedExpression() throws Exception {
//      cli.pushLineAndWaitForResults("/abc:add({a=[, b=[], c=[],)}})" + Key.LEFT.getKeyValuesAsString());
//      String out = cli.getOutput();
//      assertEquals("xxx", out);
//   }

   /**
    * Write long multiline expression and move the cursor left through the whole expression
    * Check output for expected ANSI sequence describing cursor movement and highlighting
    * @throws Exception
    */
   @Test
   public void testMultilineExpression() throws Exception {
      String command = "[{()123456789_123456789_123456789_123456789_123456789_123456789_123456789_123456789_123456789_123456789_123456789_123456789_123456789_123456789_123456789_123456789_123456789_123456789_()}]";
      CursorMovement cursorMovement = new CursorMovement.Builder()
            .left(188)
            .build();

      cli.pushLineAndWaitForResults(command + cursorMovement);
      String out = cli.getOutput();

      AnsiSequence.Builder builder = new AnsiSequence.Builder()
            .left(1).saveCursor().up(1).left(57 + d).right(30 + d).highlight('[')
            .leftAndRestore().left(1).saveCursor().up(1).left(56 + d).right(30 + d).undoHighlight('[')
            .leftAndRestore().saveCursor().up(1).left(56 + d).right(31 + d).highlight('{')
            .leftAndRestore().left(1).saveCursor().up(1).left(55 + d).right(31 + d).undoHighlight('{')
            .leftAndRestore().saveCursor().left(55 + d).right(54 + d).highlight('(')
            .leftAndRestore().left(1).saveCursor().left(54 + d).right(54 + d).undoHighlight('(')
            .leftAndRestore().saveCursor().left(54 + d).right(55 + d).highlight(')')
            .leftAndRestore().left(1).saveCursor().left(53 + d).right(55 + d).undoHighlight(')')
            .leftAndRestore();

      for (int i = 0; i < 53 + d; i++) {
         builder = builder.left(1);
      }

      builder.up(1).right(159 + d);

      for (int i = 0; i < 126 - d; i++) {
         builder = builder.left(1);
      }

      builder.saveCursor().left(33 + d).right(32 + d).highlight('(')
            .leftAndRestore().left(1).saveCursor().left(32 + d).right(32 + d).undoHighlight('(')
            .leftAndRestore().saveCursor().left(32 + d).right(33 + d).highlight(')')
            .leftAndRestore().left(1).saveCursor().left(31 + d).right(33 + d).undoHighlight(')')
            .leftAndRestore().saveCursor().down(1).left(31 + d).right(56 + d).highlight('}')
            .leftAndRestore().left(1).saveCursor().down(1).left(30 + d).right(56 + d).undoHighlight('}')
            .leftAndRestore().saveCursor().down(1).left(30 + d).right(57 + d).highlight(']')
            .leftAndRestore().down(1).right(28 + d).saveCursor().left(58 + d).right(57 + d).undoHighlight(']')
            .leftAndRestore();

      AnsiSequence expectedSequence = builder.build();

      Assert.assertTrue(out.contains(expectedSequence.toString()));
   }

   /**
    * Start cli with '--no-character-highlight' option
    * Write expression '()' and move cursor 2 to the left
    * Check output for expected ANSI sequence describing only cursor movement and no highlighting
    * @throws Exception
    */
   @Test
   public void testDisableHighlighting() throws Exception {
      CliProcessWrapper cli2 = new CliProcessWrapper()
            .addCliArgument("--connect")
            .addCliArgument("--controller=" + hostAndPort)
            .addCliArgument("--no-character-highlight");
      cli2.executeInteractive();

      String command = "()";
      CursorMovement cursorMovement = new CursorMovement.Builder()
            .left(2)
            .build();

      cli2.pushLineAndWaitForResults(command + cursorMovement);
      String out = cli2.getOutput();

      AnsiSequence expectedSequence = new AnsiSequence.Builder()
            .left(1).left(1).right(2)
            .build();

      Assert.assertTrue(out.contains(expectedSequence.toString()));
   }
}
