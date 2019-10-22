export class SpellResult {
  static extractMagic(allParagraphText) {
    const pattern = /^\s*%(\S+)\s*/g;
    try {
      const match = pattern.exec(allParagraphText);
      if (match) {
        return `%${match[1].trim()}`;
      }
    } catch (error) {
      // failed to parse, ignore
    }

    return undefined;
  }
}
