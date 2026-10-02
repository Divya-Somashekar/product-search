#!/usr/bin/env bash
# Are the service images pullable without credentials?
#
# The cluster has no imagePullSecret, so it can only pull public images. GHCR creates a package on
# first push and makes it PRIVATE by default, and there is no REST API to change that — only the
# package's own settings page. So the first release of a new service always needs a manual flip,
# and forgetting it shows up as ImagePullBackOff with a perfectly green CI run.
#
# Run this after the first release of each service. No token needed: it asks GHCR for an anonymous
# pull token, which only public packages grant.
#
#   ./check-images-public.sh                      # the two services
#   ./check-images-public.sh product-search       # or any package name
set -uo pipefail

owner=${GHCR_OWNER:-divya-somashekar}   # must be lowercase
packages=("$@")
[[ ${#packages[@]} -eq 0 ]] && packages=(catalog-service search-service)

status=0
for name in "${packages[@]}"; do
    # A private or missing package refuses to mint an anonymous token; that is a result, not an
    # error, so the failure is swallowed and the empty token falls through to the 401/403 below.
    token=$(curl -fsS "https://ghcr.io/token?scope=repository:${owner}/${name}:pull&service=ghcr.io" 2>/dev/null \
            | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
    code=$(curl -s -o /dev/null -w '%{http_code}' \
           -H "Authorization: Bearer ${token}" \
           "https://ghcr.io/v2/${owner}/${name}/tags/list")

    case "$code" in
        200) printf 'ok      %s/%s is public\n' "$owner" "$name" ;;
        401|403)
            printf 'ACTION  %s/%s is private, or has never been pushed\n' "$owner" "$name"
            printf '        make it public: https://github.com/users/%s/packages/container/%s/settings\n' \
                   "$owner" "$name"
            status=1 ;;
        404)
            printf 'ACTION  %s/%s does not exist yet - run a release first\n' "$owner" "$name"
            status=1 ;;
        *)  printf 'error   %s/%s: unexpected HTTP %s\n' "$owner" "$name" "$code"; status=1 ;;
    esac
done
exit $status
