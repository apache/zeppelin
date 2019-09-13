package org.apache.zeppelin.markdown;

import com.vladsch.flexmark.ast.Paragraph;
import com.vladsch.flexmark.ast.ParagraphContainer;
import com.vladsch.flexmark.util.ast.Block;
import com.vladsch.flexmark.util.ast.BlockContent;
import com.vladsch.flexmark.util.sequence.BasedSequence;

import java.util.List;

/**
 * Block quote which acts as a node.
 */
public class UMLBlockQuote extends Block implements ParagraphContainer {

  private BasedSequence openingMarker = BasedSequence.NULL;
  private BasedSequence openingTrailing = BasedSequence.NULL;
  private BasedSequence closingMarker = BasedSequence.NULL;
  private BasedSequence closingTrailing = BasedSequence.NULL;

  @Override
  public void getAstExtra(StringBuilder out) {
    segmentSpanChars(out, openingMarker, "open");
    segmentSpanChars(out, openingTrailing, "openTrail");
    segmentSpanChars(out, closingMarker, "close");
    segmentSpanChars(out, closingTrailing, "closeTrail");
  }

  @Override
  public BasedSequence[] getSegments() {
    return new BasedSequence[]{openingMarker, openingTrailing, closingMarker, closingTrailing};
  }

  @Override
  public boolean isParagraphEndWrappingDisabled(final Paragraph node) {
    return node == getLastChild() || node.getNext() instanceof UMLBlockQuote;
  }

  @Override
  public boolean isParagraphStartWrappingDisabled(final Paragraph node) {
    return node == getFirstChild() || node.getPrevious() instanceof UMLBlockQuote;
  }

  public UMLBlockQuote() {
  }

  public UMLBlockQuote(BasedSequence chars) {
    super(chars);
  }

  public UMLBlockQuote(BasedSequence chars, List<BasedSequence> segments) {
    super(chars, segments);
  }

  public UMLBlockQuote(BlockContent blockContent) {
    super(blockContent);
  }

  public BasedSequence getOpeningMarker() {
    return openingMarker;
  }

  public void setOpeningMarker(BasedSequence openingMarker) {
    this.openingMarker = openingMarker;
  }

  public BasedSequence getClosingMarker() {
    return closingMarker;
  }

  public void setClosingMarker(final BasedSequence closingMarker) {
    this.closingMarker = closingMarker;
  }

  public BasedSequence getOpeningTrailing() {
    return openingTrailing;
  }

  public void setOpeningTrailing(final BasedSequence openingTrailing) {
    this.openingTrailing = openingTrailing;
  }

  public BasedSequence getClosingTrailing() {
    return closingTrailing;
  }

  public void setClosingTrailing(final BasedSequence closingTrailing) {
    this.closingTrailing = closingTrailing;
  }

  @Override
  public String toString() {
    return "YUMLBlockQuote{" +
        "openingMarker=" + openingMarker +
        ", openingTrailing=" + openingTrailing +
        ", closingMarker=" + closingMarker +
        ", closingTrailing=" + closingTrailing +
        ", lineSegments=" + lineSegments +
        '}';
  }
}
