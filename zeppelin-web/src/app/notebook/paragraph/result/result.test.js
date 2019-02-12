import Result from './result.js';

describe('result', () => {
  it('should handle carriage return', () => {
    expect(new Result('Hello world').checkAndReplaceCarriageReturn()).toEqual('Hello world');
    expect(new Result('Hello world\n').checkAndReplaceCarriageReturn()).toEqual('Hello world\n');
    expect(new Result('Hello world\r\n').checkAndReplaceCarriageReturn()).toEqual('Hello world\n');
    expect(new Result('Hello\rworld\n').checkAndReplaceCarriageReturn()).toEqual('world\n');
    expect(new Result('Hello\rworld\r\n').checkAndReplaceCarriageReturn()).toEqual('world\n');
  });
});
