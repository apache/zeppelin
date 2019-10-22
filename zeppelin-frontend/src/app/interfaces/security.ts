export interface SecurityUserList {
  roles: string[];
  users: string[];
}

export interface Permissions {
  readers: string[];
  owners: string[];
  writers: string[];
  runners: string[];
}
