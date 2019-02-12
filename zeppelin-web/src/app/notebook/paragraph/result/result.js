export default class Result {
  constructor(data) {
    this.data = data;
  }

  checkAndReplaceCarriageReturn() {
    const str = this.data.replace(/\r\n/g, '\n');
    if (/\r/.test(str)) {
      let newGenerated = '';
      let strArr = str.split('\n');
      for (let str of strArr) {
        if (/\r/.test(str)) {
          let splitCR = str.split('\r');
          newGenerated += splitCR[splitCR.length - 1] + '\n';
        } else {
          newGenerated += str + '\n';
        }
      }
      // remove last "\n" character
      return newGenerated.slice(0, -1);
    } else {
      return str;
    }
  }
}
