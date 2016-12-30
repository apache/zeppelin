import Visualization from 'zeppelin-vis'
import PassthroughTransformation from 'zeppelin-tabledata/passthrough'

/**
 * Base class for visualization
 */
export default class vis1 extends Visualization {
  constructor(targetEl, config) {
    super(targetEl, config)
    this.passthrough = new PassthroughTransformation(config);
    console.log('passthrough %o', this.passthrough);
  }

  render(tableData) {
    this.targetEl.html('Vis1')
  }

  getTransformation() {
    return this.passthrough
  }
}
