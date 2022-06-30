// const baseUrl = window.location.host === 's3-connector.narrative.tools' ?
//   'https://aws-s3.narrativeconnectors.com' :
//   'https://aws-s3-dev.narrativeconnectors.com'
const baseUrl = "https://localhost:9002"

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