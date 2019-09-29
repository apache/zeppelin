module.exports = {
  devServer: {
    historyApiFallback: true,
    port: process.env.VUE_APP_WEB_PORT,
    noInfo: true,
    overlay: true
  }
}