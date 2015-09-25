package org.apache.zeppelin.rest;

import org.apache.zeppelin.server.JsonResponse;
import org.apache.zeppelin.ticket.TicketContainer;
import org.apache.shiro.SecurityUtils;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;

/**
 * Zeppelin root rest api endpoint.
 *
 * @author anthonycorbacho
 * @since 0.3.4
 */
@Path("/security")
@Produces("application/json")
public class SecurityRestApi {
  /**
   * Required by Swagger.
   */
  public SecurityRestApi() {
    super();
  }

  /**
   * Get ticket
   * Returns username & ticket
   * for anonymous access, username is always anonymous.
   * After getting this ticket, access through websockets become safe
   * @return 200 response
   */
  @GET
  @Path("ticket")
  public Response ticket() {
    Object oprincipal = SecurityUtils.getSubject().getPrincipal();
    String principal;
    if (oprincipal == null)
      principal = "anonymous";
    else
      principal = oprincipal.toString();
    String ticket = TicketContainer.instance.getTicket(principal);
    Map<String, String> data = new HashMap<>();
    data.put("principal", principal);
    data.put("ticket", ticket);

    return new JsonResponse(Response.Status.OK, "", data).build();
  }
}
