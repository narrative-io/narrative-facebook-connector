const baseUrl = window.location.host === 'facebook-connector.narrative.tools' ?
  'https://facebook.narrativeconnectors.com' :
  'https://facebook-dev.narrativeconnectors.com'
// const baseUrl = "https://localhost:9002"

let headers = null

function setHeaders(token) {
	headers = {
		headers: {
			'Authorization': `Bearer ${token}`
		}
	}
}

function getHeaders() {
	return headers
}

export {
	baseUrl,
	setHeaders,
	getHeaders
}
