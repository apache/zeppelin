export default class Result {
  constructor(data) {
    this.data = data;
  }

  checkAndReplaceCarriageReturn() {
    return this.data.replace(/(\r\n)/gm, '\n');
  }
}
