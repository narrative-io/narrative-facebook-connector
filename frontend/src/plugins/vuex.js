import Vue from 'vue'
import Vuex from 'vuex'
import ServicesStore from '@narrative.io/tackle-box/src/modules/app/store/servicesStore'

Vue.use(Vuex)

let store = new Vuex.Store({
	state: {},
	mutations: {},
  actions: {},
	getters: {},
	modules: {
		nioServices: ServicesStore
	}
})

export default store