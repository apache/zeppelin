module.exports = {
    entry: ['./'],
    output: {
        path: './',
        filename: 'vis.bundle.js',
    },
    resolve: {
        root: __dirname + "/node_modules",
        extensions: [".js"]
    },
    module: {
        loaders: [{
            test: /\.js$/,
            //exclude: /node_modules/,
            loader: 'babel-loader'
        }]
    }
}
