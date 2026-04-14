window.QuayReactiveParameter = (function () {
    function getCrumb() {
        if (typeof crumb !== 'undefined' && crumb && typeof crumb.wrap === 'function') {
            return crumb;
        }
        return null;
    }

    function buildHeaders() {
        var headers = { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' };
        var c = getCrumb();
        if (c && c.value && c.fieldName) {
            headers[c.fieldName] = c.value;
        }
        return headers;
    }

    function encodeParams(params) {
        var parts = [];
        for (var k in params) {
            if (Object.prototype.hasOwnProperty.call(params, k) && params[k] != null) {
                parts.push(encodeURIComponent(k) + '=' + encodeURIComponent(params[k]));
            }
        }
        var c = getCrumb();
        if (c && c.value && c.fieldName) {
            parts.push(encodeURIComponent(c.fieldName) + '=' + encodeURIComponent(c.value));
        }
        return parts.join('&');
    }

    function setStatus(container, message) {
        var status = container.querySelector('.quay-status');
        if (status) {
            var ref = status.querySelector('.quay-image-ref');
            if (ref) ref.textContent = message;
        }
    }

    function findExternalRepoSource(container) {
        var scope = container.closest('form, .jenkins-form, body') || document;
        var NAMES = ['REPO_NAME', 'repo', 'repoName', 'repository'];

        var wrappers = scope.querySelectorAll('[name="parameter"]');
        for (var i = 0; i < wrappers.length; i++) {
            var w = wrappers[i];
            if (container.contains(w) || w.contains(container)) continue;
            var nameInput = w.querySelector('input[name="name"]');
            if (!nameInput) continue;
            if (NAMES.indexOf(nameInput.value) === -1) continue;
            var valueEl = w.querySelector('[name="value"]');
            if (valueEl) return valueEl;
        }

        for (var j = 0; j < NAMES.length; j++) {
            var candidates = scope.querySelectorAll('[name="' + NAMES[j] + '"]');
            for (var k = 0; k < candidates.length; k++) {
                if (!container.contains(candidates[k])) return candidates[k];
            }
        }
        return null;
    }

    function getRepoValue(container, externalEl) {
        if (externalEl) return (externalEl.value || '').trim();
        var repoInput = container.querySelector('.quay-repo-input');
        return repoInput ? (repoInput.value || '').trim() : '';
    }

    function refreshTags(container) {
        var url = container.getAttribute('data-fetch-url');
        var endpoint = container.getAttribute('data-quay-endpoint') || 'quay.io';
        var organization = container.getAttribute('data-organization') || '';
        var credentialsId = container.getAttribute('data-credentials-id') || '';
        var tagLimit = container.getAttribute('data-tag-limit') || '20';
        var defaultTag = container.getAttribute('data-default-tag') || '';

        var repoInput = container.querySelector('.quay-repo-input');
        var select = container.querySelector('.quay-tag-select');
        if (!select) return;

        var external = findExternalRepoSource(container);
        var repository = getRepoValue(container, external);

        if (repoInput) repoInput.value = repository;

        setStatus(container, endpoint + '/' + organization + '/' + repository + ' (loading…)');

        if (!repository) {
            select.innerHTML = '<option value="">-- Enter repository --</option>';
            return;
        }

        var body = encodeParams({
            quayEndpoint: endpoint,
            organization: organization,
            repository: repository,
            credentialsId: credentialsId,
            tagLimit: tagLimit
        });

        fetch(url, {
            method: 'POST',
            headers: buildHeaders(),
            credentials: 'same-origin',
            body: body
        }).then(function (resp) {
            if (!resp.ok) throw new Error('HTTP ' + resp.status);
            return resp.json();
        }).then(function (data) {
            select.innerHTML = '';
            var tags = (data && data.tags) || [];
            if (data && data.error && data.error !== null) {
                var errOpt = document.createElement('option');
                errOpt.value = '';
                errOpt.textContent = '-- Error: ' + data.error + ' --';
                select.appendChild(errOpt);
            } else if (tags.length === 0) {
                var noOpt = document.createElement('option');
                noOpt.value = '';
                noOpt.textContent = '-- No tags found --';
                select.appendChild(noOpt);
            } else {
                tags.forEach(function (name) {
                    var opt = document.createElement('option');
                    opt.value = name;
                    opt.textContent = name;
                    if (name === defaultTag) opt.selected = true;
                    select.appendChild(opt);
                });
            }
            setStatus(container, endpoint + '/' + organization + '/' + repository);
        }).catch(function (err) {
            select.innerHTML = '';
            var opt = document.createElement('option');
            opt.value = '';
            opt.textContent = '-- Error: ' + err.message + ' --';
            select.appendChild(opt);
            setStatus(container, endpoint + '/' + organization + '/' + repository + ' (failed)');
        });
    }

    function debounce(fn, wait) {
        var t;
        return function () {
            var args = arguments, ctx = this;
            clearTimeout(t);
            t = setTimeout(function () { fn.apply(ctx, args); }, wait);
        };
    }

    function hideRepoField(container) {
        var repoInput = container.querySelector('.quay-repo-input');
        if (!repoInput) return;
        if (repoInput.type === 'hidden') return;
        var wrapper = repoInput.closest('div');
        if (wrapper && wrapper !== container) wrapper.style.display = 'none';
    }

    function init(container) {
        if (container.dataset.quayReactiveInitialized === 'true') return;
        container.dataset.quayReactiveInitialized = 'true';

        var external = findExternalRepoSource(container);
        var debounced = debounce(function () { refreshTags(container); }, 400);

        if (external) {
            hideRepoField(container);
            external.addEventListener('input', debounced);
            external.addEventListener('change', function () { refreshTags(container); });
        } else {
            var repoInput = container.querySelector('.quay-repo-input');
            if (repoInput) {
                repoInput.addEventListener('input', debounced);
                repoInput.addEventListener('change', function () { refreshTags(container); });
            }
        }
        refreshTags(container);
    }

    function refreshRepos(container) {
        var url = container.getAttribute('data-fetch-url');
        var endpoint = container.getAttribute('data-quay-endpoint') || 'quay.io';
        var organization = container.getAttribute('data-organization') || '';
        var credentialsId = container.getAttribute('data-credentials-id') || '';
        var defaultRepo = container.getAttribute('data-default-repository') || '';

        var select = container.querySelector('.quay-repo-select');
        if (!select) return;

        var body = encodeParams({
            quayEndpoint: endpoint,
            organization: organization,
            credentialsId: credentialsId
        });

        fetch(url, {
            method: 'POST',
            headers: buildHeaders(),
            credentials: 'same-origin',
            body: body
        }).then(function (resp) {
            if (!resp.ok) throw new Error('HTTP ' + resp.status);
            return resp.json();
        }).then(function (data) {
            select.innerHTML = '';
            var repos = (data && data.repositories) || [];
            if (data && data.error && data.error !== null) {
                var errOpt = document.createElement('option');
                errOpt.value = '';
                errOpt.textContent = '-- Error: ' + data.error + ' --';
                select.appendChild(errOpt);
            } else if (repos.length === 0) {
                var noOpt = document.createElement('option');
                noOpt.value = '';
                noOpt.textContent = '-- No repositories found --';
                select.appendChild(noOpt);
            } else {
                repos.forEach(function (name) {
                    var opt = document.createElement('option');
                    opt.value = name;
                    opt.textContent = name;
                    if (name === defaultRepo) opt.selected = true;
                    select.appendChild(opt);
                });
                select.dispatchEvent(new Event('change', { bubbles: true }));
            }
        }).catch(function (err) {
            select.innerHTML = '';
            var opt = document.createElement('option');
            opt.value = '';
            opt.textContent = '-- Error: ' + err.message + ' --';
            select.appendChild(opt);
        });
    }

    function initRepo(container) {
        if (container.dataset.quayRepoInitialized === 'true') return;
        container.dataset.quayRepoInitialized = 'true';
        refreshRepos(container);
    }

    return { init: init, initRepo: initRepo, refreshTags: refreshTags, refreshRepos: refreshRepos };
})();
