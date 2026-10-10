export interface IUpstreamService<T, P = string> {
  name: string;
  fetch(param?: P): Promise<T>;
}
