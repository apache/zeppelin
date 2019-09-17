/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.markdown;

import com.vladsch.flexmark.ext.gitlab.internal.GitLabOptions;
import com.vladsch.flexmark.parser.InlineParser;
import com.vladsch.flexmark.parser.block.AbstractBlockParser;
import com.vladsch.flexmark.parser.block.BlockContinue;
import com.vladsch.flexmark.parser.block.BlockParser;
import com.vladsch.flexmark.parser.block.BlockParserFactory;
import com.vladsch.flexmark.parser.block.AbstractBlockParserFactory;
import com.vladsch.flexmark.parser.block.BlockStart;
import com.vladsch.flexmark.parser.block.MatchedBlockParser;
import com.vladsch.flexmark.parser.block.ParserState;
import com.vladsch.flexmark.parser.block.CustomBlockParserFactory;
import com.vladsch.flexmark.util.ast.Block;
import com.vladsch.flexmark.util.ast.BlockContent;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.DataHolder;
import com.vladsch.flexmark.util.sequence.BasedSequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser to get the Block content
 */
public class UMLBlockQuoteParser extends AbstractBlockParser {

  private static final Logger LOGGER = LoggerFactory.getLogger(UMLBlockQuoteParser.class);


  private static Pattern YUML_BLOCK_START = Pattern.compile("(%%%)\\s+(.*\\n)");
  private static Pattern YUML_BLOCK_END = Pattern.compile("%%%(\\s*$)");

  private final UMLBlockQuote block = new UMLBlockQuote();
  private BlockContent content = new BlockContent();
  private final GitLabOptions options;
  private boolean hadClose = false;

  UMLBlockQuoteParser(DataHolder options, BasedSequence openMarker, BasedSequence openTrailing) {
    this.options = new GitLabOptions(options);
    this.block.setOpeningMarker(openMarker);
    this.block.setOpeningTrailing(openTrailing);
  }

  @Override
  public Block getBlock() {
    return block;
  }

  @Override
  public BlockContinue tryContinue(ParserState state) {
    if (hadClose) {
      return BlockContinue.none();
    }

    final int index = state.getIndex();

    BasedSequence line = state.getLineWithEOL();
    final Matcher matcher = YUML_BLOCK_END.matcher(line.subSequence(index));
    if (!matcher.matches()) {
      return BlockContinue.atIndex(index);
    } else {
      // if have open gitlab block quote last child then let them handle it
      Node lastChild = block.getLastChild();
      if (lastChild instanceof UMLBlockQuote) {
        final BlockParser parser = state.getActiveBlockParser((Block) lastChild);
        if (parser instanceof UMLBlockQuoteParser && !((UMLBlockQuoteParser) parser).hadClose) {
          // let the child handle it
          return BlockContinue.atIndex(index);
        }
      }
      hadClose = true;
      block.setClosingMarker(state.getLine().subSequence(index, index + 3));
      block.setClosingTrailing(state.getLineWithEOL().subSequence(matcher.start(1),
          matcher.end(1)));
      return BlockContinue.atIndex(state.getLineEndIndex());
    }
  }

  @Override
  public void addLine(ParserState state, BasedSequence line) {
    content.add(line, state.getIndent());
  }

  @Override
  public void closeBlock(ParserState state) {
    block.setContent(content);
    block.setCharsFromContent();
    content = null;
  }

  @Override
  public boolean isContainer() {
    return true;
  }

  @Override
  public boolean canContain(final ParserState state, final BlockParser blockParser,
                            final Block block) {
    return true;
  }

  @Override
  public void parseInlines(InlineParser inlineParser) {
  }

  /**
   * Generic Factory
   */
  public static class Factory implements CustomBlockParserFactory {
    @Override
    public Set<Class<? extends CustomBlockParserFactory>> getAfterDependents() {
      return null;
    }

    @Override
    public Set<Class<? extends CustomBlockParserFactory>> getBeforeDependents() {
      return null;
    }

    @Override
    public boolean affectsGlobalScope() {
      return false;
    }

    @Override
    public BlockParserFactory apply(DataHolder options) {
      return new UMLBlockQuoteParser.BlockFactory(options);
    }
  }

  private static class BlockFactory extends AbstractBlockParserFactory {
    private final GitLabOptions options;

    BlockFactory(DataHolder options) {
      super(options);
      this.options = new GitLabOptions(options);
    }

    boolean haveBlockQuoteParser(ParserState state) {
      final List<BlockParser> parsers = state.getActiveBlockParsers();
      int i = parsers.size();
      while (i-- > 0) {
        if (parsers.get(i) instanceof UMLBlockQuoteParser) {
          return true;
        }
      }
      return false;
    }

    @Override
    public BlockStart tryStart(ParserState state, MatchedBlockParser matchedBlockParser) {
      if (options.nestedBlockQuotes || !haveBlockQuoteParser(state)) {
        BasedSequence line = state.getLineWithEOL();
        final Matcher matcher = YUML_BLOCK_START.matcher(line);
        if (matcher.matches()) {
          LOGGER.debug("Matcher group count {} ", matcher.groupCount());
          return BlockStart.of(new UMLBlockQuoteParser(state.getProperties(),
              line.subSequence(0, 3), line.subSequence(4, line.length())))
              .atIndex(state.getLineEndIndex());
        }
      }
      return BlockStart.none();
    }
  }
}
