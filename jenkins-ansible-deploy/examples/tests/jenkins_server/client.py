"""Minimal Jenkins REST client for the integration tests and the setup script (standard library only)."""
import http.cookiejar
import json
import time
import urllib.error
import urllib.parse
import urllib.request


class Jenkins:
    def __init__(self, url):
        self.url = url.rstrip('/')
        # Jenkins issues the CSRF crumb per session, so the session cookie must be kept
        self._opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()), urllib.request.ProxyHandler({}))
        self._crumb_header = None

    def request(self, path, data=None, headers=None):
        """POST when data is given, GET otherwise. Returns (status, body, response headers)."""
        all_headers = dict(headers or {})
        if data is not None:
            all_headers.update(self._crumb())
            if isinstance(data, dict):
                data = urllib.parse.urlencode(data, doseq=True).encode()
        http_request = urllib.request.Request(self.url + path, data=data, headers=all_headers)
        try:
            with self._opener.open(http_request) as response:
                return response.status, response.read().decode(), response.headers
        except urllib.error.HTTPError as error:
            return error.code, error.read().decode(), error.headers

    def _crumb(self):
        if self._crumb_header is None:
            with self._opener.open(self.url + '/crumbIssuer/api/json') as response:
                crumb = json.loads(response.read())
            self._crumb_header = {crumb['crumbRequestField']: crumb['crumb']}
        return self._crumb_header

    def json(self, path, tree=None):
        query = '?tree=' + urllib.parse.quote(tree) if tree else ''
        status, body, _ = self.request(path + '/api/json' + query)
        if status != 200:
            raise RuntimeError(f'GET {path}: HTTP {status}')
        return json.loads(body)

    def is_ready(self):
        try:
            return self.request('/api/json')[0] == 200
        except OSError:
            return False

    def run_script(self, groovy):
        """Run a Groovy script in the script console and return its output."""
        status, body, _ = self.request('/scriptText', {'script': groovy})
        if status != 200:
            raise RuntimeError(f'script console: HTTP {status}: {body[:500]}')
        return body

    def create_job(self, name, config_xml):
        status, body, _ = self.request(f'/createItem?name={urllib.parse.quote(name)}', config_xml.encode(),
                                       {'Content-Type': 'application/xml'})
        if status != 200:
            raise RuntimeError(f'create job {name}: HTTP {status}: {body[:500]}')

    def delete_job(self, name):
        self.request(f'/job/{name}/doDelete', b'')

    def start_build(self, job, params=None):
        """Queue a build and return its number once it has left the queue."""
        if params is None:
            status, body, headers = self.request(f'/job/{job}/build', b'')
        else:
            status, body, headers = self.request(f'/job/{job}/buildWithParameters', params)
        location = headers.get('Location')
        if status != 201 or not location:
            raise RuntimeError(f'start {job}: HTTP {status}: {body[:500]}')
        queue_path = urllib.parse.urlparse(location).path.rstrip('/')
        for _ in range(120):
            item = self.json(queue_path)
            if item.get('executable'):
                return item['executable']['number']
            time.sleep(1)
        raise TimeoutError(f'{job}: build did not start')

    def build_info(self, job, number):
        return self.json(f'/job/{job}/{number}', 'building,result,description,artifacts[relativePath]')

    def wait_for_build(self, job, number, timeout_seconds=600, queue_seconds=60):
        """Wait until the build finishes and return its result (SUCCESS, UNSTABLE, FAILURE, ABORTED, NOT_BUILT).
        A build still in the queue does not exist yet (HTTP 404); wait for it at most queue_seconds."""
        started = time.time()
        while time.time() - started < timeout_seconds:
            status, body, _ = self.request(f'/job/{job}/{number}/api/json?tree=building,result')
            if status == 200:
                build = json.loads(body)
                if not build['building']:
                    return build['result']
            elif status != 404 or time.time() - started > queue_seconds:
                raise RuntimeError(f'{job} #{number}: HTTP {status}')
            time.sleep(1)
        raise TimeoutError(f'{job} #{number} did not finish')

    def wait_for_console(self, job, number, text, timeout_seconds=120):
        """Wait until the console log contains text, reading only the part not read yet."""
        deadline = time.time() + timeout_seconds
        start, seen = 0, ''
        while time.time() < deadline:
            status, body, headers = self.request(f'/job/{job}/{number}/logText/progressiveText?start={start}')
            if status == 200:
                seen += body
                start = int(headers.get('X-Text-Size', start))
                if text in seen:
                    return
            time.sleep(1)
        raise TimeoutError(f'{job} #{number}: console never showed {text!r}')

    def wait_for_input(self, job, number, timeout_seconds=120):
        """Wait until the build pauses at an input step; return [{'id', 'message', 'names'}]."""
        deadline = time.time() + timeout_seconds
        tree = 'building,actions[executions[id,input[message,parameters[name]]]]'
        while time.time() < deadline:
            data = self.json(f'/job/{job}/{number}', tree)
            pending = [{'id': execution['id'], 'message': execution['input']['message'],
                        'names': [p['name'] for p in execution['input'].get('parameters') or []]}
                       for action in data.get('actions') or [] if action
                       for execution in action.get('executions') or []]
            if pending:
                return pending
            if not data['building']:
                raise RuntimeError(f'{job} #{number} finished without waiting for input')
            time.sleep(1)
        raise TimeoutError(f'{job} #{number}: no input step')

    def submit_input(self, job, number, input_id, values):
        parameters = [{'name': name, 'value': value} for name, value in values.items()]
        status, body, _ = self.request(f'/job/{job}/{number}/input/{input_id}/proceed',
                                       {'json': json.dumps({'parameter': parameters})})
        if status not in (200, 302):
            raise RuntimeError(f'submit input: HTTP {status}: {body[:500]}')

    def abort_input(self, job, number, input_id):
        self.request(f'/job/{job}/{number}/input/{input_id}/abort', b'')

    def stop_build(self, job, number):
        self.request(f'/job/{job}/{number}/stop', b'')

    def console(self, job, number):
        status, body, _ = self.request(f'/job/{job}/{number}/consoleText')
        if status != 200:
            raise RuntimeError(f'console of {job} #{number}: HTTP {status}')
        return body

    def artifact(self, job, number, path):
        status, body, _ = self.request(f'/job/{job}/{number}/artifact/{path}')
        if status != 200:
            raise RuntimeError(f'artifact {path}: HTTP {status}')
        return body

    def next_build_number(self, job):
        return self.json(f'/job/{job}', 'nextBuildNumber')['nextBuildNumber']

    def parameter_names(self, job):
        data = self.json(f'/job/{job}', 'property[parameterDefinitions[name]]')
        return [definition['name'] for job_property in data['property']
                for definition in job_property.get('parameterDefinitions') or []]

    def config_xml(self, job):
        status, body, _ = self.request(f'/job/{job}/config.xml')
        if status != 200:
            raise RuntimeError(f'config.xml of {job}: HTTP {status}')
        return body
