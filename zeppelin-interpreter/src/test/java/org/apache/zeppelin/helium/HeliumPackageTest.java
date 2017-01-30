package org.apache.zeppelin.helium;

import com.google.gson.Gson;
import org.junit.Test;

import static org.junit.Assert.*;

public class HeliumPackageTest {

  private Gson gson = new Gson();

  @Test
  public void parseSpellPackageInfo() {
    String exampleSpell = "{\n" +
        "  \"type\" : \"SPELL\",\n" +
        "  \"name\" : \"echo-spell\",\n" +
        "  \"description\" : \"'%echo' - return just what receive (example)\",\n" +
        "  \"artifact\" : \"./zeppelin-examples/zeppelin-example-spell-echo\",\n" +
        "  \"license\" : \"Apache-2.0\",\n" +
        "  \"icon\" : \"<i class='fa fa-repeat'></i>\",\n" +
        "  \"spell\": {\n" +
        "    \"magic\": \"%echo\",\n" +
        "    \"usage\": \"%echo <TEXT>\"\n" +
        "  }\n" +
        "}";

    HeliumPackage p = gson.fromJson(exampleSpell, HeliumPackage.class);
    assertEquals(p.getSpellInfo().getMagic(), "%echo");
    assertEquals(p.getSpellInfo().getUsage(), "%echo <TEXT>");
  }
}