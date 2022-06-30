import Vue from 'vue'
import VueRouter from 'vue-router'
import routes from './routes'
import { NioRouterModule } from '@narrative.io/tackle-box'

let router = new VueRouter({
  routes,
  mode: 'history'
})

NioRouterModule.setupRouter(router)

Vue.use(VueRouter)

export default router