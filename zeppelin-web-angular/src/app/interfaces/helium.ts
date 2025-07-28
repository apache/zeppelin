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
