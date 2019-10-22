export class ITicket {
  principal = '';
  ticket = '';
  redirectURL = '';
  roles = '';
}

export class IZeppelinVersion {
  'git-commit-id': string;
  'git-timestamp': string;
  'version': string;
}

export class ITicketWrapped extends ITicket {
  init = false;
  screenUsername = '';
}
