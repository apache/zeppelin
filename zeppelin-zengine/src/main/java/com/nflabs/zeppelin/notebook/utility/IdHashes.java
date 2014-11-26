package com.nflabs.zeppelin.notebook.utility;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Generate Tiny ID.
 * 
 * @author anthonycorbacho
 *
 */
public class IdHashes {
  public static final char[] DICTIONARY = new char[] {'1', '2', '3', '4', '5', '6', '7', '8', '9',
    'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'J', 'K', 'M', 'N', 'P', 'Q', 'R', 'S', 'T', 'U',
    'V', 'W', 'X', 'Y', 'Z'};

  /**
   * encodes the given string into the base of the dictionary provided in the constructor.
   * 
   * @param value the number to encode.
   * @return the encoded string.
   */
  public static String encode(Long value) {

    List<Character> result = new ArrayList<Character>();
    BigInteger base = new BigInteger("" + DICTIONARY.length);
    int exponent = 1;
    BigInteger remaining = new BigInteger(value.toString());
    while (true) {
      BigInteger a = base.pow(exponent); // 16^1 = 16
      BigInteger b = remaining.mod(a); // 119 % 16 = 7 | 112 % 256 = 112
      BigInteger c = base.pow(exponent - 1);
      BigInteger d = b.divide(c);

      // if d > dictionary.length, we have a problem. but BigInteger doesnt have
      // a greater than method :-( hope for the best. theoretically, d is always
      // an index of the dictionary!
      result.add(DICTIONARY[d.intValue()]);
      remaining = remaining.subtract(b); // 119 - 7 = 112 | 112 - 112 = 0

      // finished?
      if (remaining.equals(BigInteger.ZERO)) {
        break;
      }

      exponent++;
    }

    // need to reverse it, since the start of the list contains the least significant values
    StringBuffer sb = new StringBuffer();
    for (int i = result.size() - 1; i >= 0; i--) {
      sb.append(result.get(i));
    }
    return sb.toString();
  }
}
