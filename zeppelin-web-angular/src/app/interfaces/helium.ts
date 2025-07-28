import { HeliumPackageType } from '@zeppelin/helium';

export type HeliumType = 'VISUALIZATION';

interface HeliumPackage {
  name: string;
  artifact: string;
  description: string;
  icon: string;
  type: HeliumPackageType;
  license: string;
  published: string;
}

export interface HeliumPackageSearchResult {
  registry: string;
  pkg: HeliumPackage;
  enabled: boolean;
}

export interface HeliumBundle {
  id: string;
  name: string;
  icon: string;
  type: HeliumType;
  // tslint:disable-next-line:no-any
  class: any;
}
