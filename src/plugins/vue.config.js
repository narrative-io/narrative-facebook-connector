const WorkerPlugin = require('worker-plugin')
module.exports = {
	"transpileDependencies": [
    "vuetify"
  ],
  lintOnSave: false,
  configureWebpack: {
    output: {
      globalObject: "this"
    },
    plugins: [
      new WorkerPlugin()
    ]
	}
}
