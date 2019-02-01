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

/**
 * This is a tool for creating ANSI codes sequences for BracketsHighlightingTestCase.
 *
 * @author Tomas Terem tterem@redhat.com
 */
public class AnsiSequence {

   /**
    * Builder methods and related ANSI codes
    * left(N)              Esc[<N>D                 Move the cursor N columns to the left
    * right(N)             Esc[<N>C                 Move the cursor N columns to the right
    * up(N)                Esc[<N>A                 Move the cursor up N lines
    * down(N)              Esc[<N>B                 Move the cursor down N lines
    * saveCursor()         Esc7                     Save cursor position and attributes
    * restoreCursor()      Esc8                     Restore cursor position and attributes (to the last saved position)
    * highlight(C)         Esc[1mEsc[;39;42m<C>     Highlight character C - text color to white, background color to green, bold on
    * undoHighlight(C)     Esc[0;22mEsc[;39;49m<C>  Remove highlighting from character C - reset colors to default, bold off
    * (Esc is an Escape character(ascii code \033 in oct, 27 in dec) - shows as a square in terminal/ide)
    */
   public static class Builder {

      private static final char ESC = (char) 27;
      private StringBuilder sequence = new StringBuilder();

      public Builder() { }

      /**
       * Move cursor n columns to the left
       */
      public Builder left(int n) {
         sequence.append(ESC)
               .append("[")
               .append(n)
               .append("D");
         return this;
      }

      /**
       * Move cursor n columns to the right
       */
      public Builder right(int n) {
         sequence.append(ESC)
               .append("[")
               .append(n)
               .append("C");
         return this;
      }

      /**
       * Move cursor n lines up
       */
      public Builder up(int n) {
         sequence.append(ESC)
               .append("[")
               .append(n)
               .append("A");
         return this;
      }

      /**
       * Move cursor n lines down
       */
      public Builder down(int n) {
         sequence.append(ESC)
               .append("[")
               .append(n)
               .append("B");
         return this;
      }

      /**
       * Save cursor current position
       */
      public Builder saveCursor() {
         sequence.append(ESC)
               .append(7);
         return this;
      }

      /**
       * Restore cursor to previously saved position
       */
      public Builder restoreCursor() {
         sequence.append(ESC)
               .append(8);
         return this;
      }

      /**
       * Highlight current (cursor is on it) character c
       */
      public Builder highlight(char c) {
         sequence.append(ESC)
               .append("[1m")
               .append(ESC)
               .append("[;39;42m")
               .append(c);
         return this;
      }

      /**
       * Remove highlighting from current (cursor is on it) character c
       */
      public Builder undoHighlight(char c) {
         sequence.append(ESC)
               .append("[0;22m")
               .append(ESC)
               .append("[;39;49m")
               .append(c);
         return this;
      }

      /**
       * Commonly used compound sequence.
       * Move cursor 1 to the left and restore cursor to previously saved position (move to the left has no effect)
       */
      public Builder leftAndRestore() {
         return this.left(1).restoreCursor();
      }

      public AnsiSequence build() {
         return new AnsiSequence(sequence.toString());
      }

   }

   private String ansiSequence;

   private AnsiSequence(String ansiSequence) {
      this.ansiSequence = ansiSequence;
   }

   @Override
   public String toString() {
      return ansiSequence;
   }
}
