let webpack = require('webpack')

const fs = require('fs')
const packageJson = fs.readFileSync('./package.json')
const tbPackageJson = fs.readFileSync('./node_modules/@narrative.io/tackle-box/package.json')
const appVersion = JSON.parse(packageJson).version || 0
const tbVersion = JSON.parse(tbPackageJson).version || 0

const {gitDescribeSync} = require('git-describe');

module.exports = {
	"transpileDependencies": [
    "vuetify"
  ],
  lintOnSave: false,
  configureWebpack: {
		plugins: [
      new webpack.DefinePlugin({
        'process.env': {
          APP_VERSION: '"' + appVersion + '"',
          TACKLEBOX_VERSION: '"' + tbVersion + '"',
          APP_GIT_HASH: '"' + gitDescribeSync(__dirname, {
            longSemver: true,
            dirtySemver: false
          }).hash.replace('dirty', '') + '"'
        }
      })
    ],
    output: {
      globalObject: "this"
    }
	}
};