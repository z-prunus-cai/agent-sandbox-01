#!/usr/bin/env bash
# 把 Jenkins、Ansible、Jenkins Ansible 插件的文档下载到 docs/jenkins-ansible/upstream/。
# 下载的文档不进 git（已在 .gitignore 中忽略），需要时运行本脚本重新下载。
#
# 依赖：git、curl、python3（含 beautifulsoup4、markdownify，用于把 HTML 转成 Markdown），
#       ansible-core（用 ansible-doc 生成 ansible.builtin 文档；没有安装则跳过这一项）。
#   pip install beautifulsoup4 markdownify ansible-core
#
# 用法：docs/jenkins-ansible/fetch-docs.sh [输出目录]
set -euo pipefail

OUT="${1:-$(cd "$(dirname "$0")" && pwd)/upstream}"
WORK="$(mktemp -d)"
trap 'rm -rf -- "${WORK:?}"' EXIT
mkdir -p "$OUT"
SOURCES="$OUT/SOURCES.txt"
echo "下载时间：$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$SOURCES"

# 稀疏克隆一个仓库的某个子目录，并记录提交号
sparse_clone() {  # <仓库 URL> <子目录> <本地目录名>
    git clone -q --depth 1 --filter=blob:none --sparse "$1" "$WORK/$3"
    git -C "$WORK/$3" sparse-checkout set "$2"
    echo "$1 $2 @ $(git -C "$WORK/$3" rev-parse HEAD)" >> "$SOURCES"
}

# 把 jenkins.io 的页面转成 Markdown，只保留正文
html_to_md() {  # <URL> <输出文件>
    curl -sSfL "$1" -o "$WORK/page.html"
    python3 -I - "$WORK/page.html" "$2" "$1" <<'EOF'
import re, sys
from bs4 import BeautifulSoup
from markdownify import markdownify
src, dst, url = sys.argv[1:4]
soup = BeautifulSoup(open(src, encoding="utf-8").read(), "html.parser")
for tag in soup(["script", "style", "nav", "header", "footer", "noscript", "svg"]):
    tag.decompose()
main = (soup.select_one("#main-content") or soup.select_one("main")
        or soup.select_one(".container .col-lg-9") or soup.body)
text = re.sub(r"\n{3,}", "\n\n", markdownify(str(main), heading_style="ATX", code_language="groovy"))
with open(dst, "w", encoding="utf-8") as f:
    f.write(f"<!-- Source: {url} -->\n\n" + text.strip() + "\n")
EOF
}

echo "==> Jenkins 文档"
sparse_clone https://github.com/jenkins-infra/jenkins.io.git content/doc jenkins.io
mkdir -p "$OUT/jenkins"
rm -rf -- "${OUT:?}/jenkins/handbook"
cp -r "$WORK/jenkins.io/content/doc" "$OUT/jenkins/handbook"

echo "==> Jenkins Pipeline 步骤参考"
mkdir -p "$OUT/jenkins/pipeline-steps"
for p in workflow-basic-steps workflow-durable-task-step workflow-cps workflow-scm-step \
         pipeline-input-step pipeline-utility-steps pipeline-build-step pipeline-stage-step \
         pipeline-milestone-step pipeline-model-definition credentials-binding ssh-agent \
         lockable-resources git timestamper ws-cleanup; do
    html_to_md "https://www.jenkins.io/doc/pipeline/steps/$p/" "$OUT/jenkins/pipeline-steps/$p.md"
done
html_to_md https://www.jenkins.io/doc/pipeline/steps/ "$OUT/jenkins/pipeline-steps/INDEX-all-plugins.md"

echo "==> Ansible 文档"
sparse_clone https://github.com/ansible/ansible-documentation.git docs/docsite/rst ansible-documentation
mkdir -p "$OUT/ansible"
rm -rf -- "${OUT:?}/ansible/docsite"
cp -r "$WORK/ansible-documentation/docs/docsite/rst" "$OUT/ansible/docsite"

if command -v ansible-doc >/dev/null; then
    echo "==> ansible.builtin 模块与插件参考（ansible-doc）"
    echo "ansible-doc: $(ansible-doc --version | head -1)" >> "$SOURCES"
    python3 -I - "$OUT/ansible/builtin-plugins" <<'EOF'
import json, os, subprocess, sys
out = sys.argv[1]
env = dict(os.environ, ANSIBLE_NOCOLOR="1", ANSIBLE_PAGER="cat", PAGER="cat")
def doc(*args):
    return subprocess.run(["ansible-doc", *args], capture_output=True, text=True, env=env)
for kind in ["module", "filter", "test", "lookup", "callback", "connection", "inventory",
             "become", "cache", "shell", "strategy", "vars"]:
    names = sorted(n for n in json.loads(doc("-t", kind, "-l", "-j").stdout or "{}")
                   if n.startswith("ansible.builtin."))
    os.makedirs(os.path.join(out, kind), exist_ok=True)
    for name in names:
        result = doc("-t", kind, name)
        if result.returncode == 0 and result.stdout.strip():
            path = os.path.join(out, kind, name.removeprefix("ansible.builtin.") + ".txt")
            with open(path, "w") as f:
                f.write(result.stdout)
    print(f"    {kind}: {len(names)}")
keywords = sorted(json.loads(doc("-t", "keyword", "-l", "-j").stdout or "{}"))
with open(os.path.join(out, "keywords.txt"), "w") as f:
    f.write(doc("-t", "keyword", *keywords).stdout)
EOF
else
    echo "==> 未安装 ansible-core，跳过 ansible.builtin 参考"
fi

echo "==> Jenkins Ansible 插件文档"
git clone -q --depth 1 https://github.com/jenkinsci/ansible-plugin.git "$WORK/ansible-plugin"
echo "https://github.com/jenkinsci/ansible-plugin @ $(git -C "$WORK/ansible-plugin" rev-parse HEAD)" >> "$SOURCES"
P="$OUT/ansible-plugin"
mkdir -p "$P/docs" "$P/help"
cp "$WORK/ansible-plugin/README.md" "$WORK/ansible-plugin/LICENSE.md" "$P/"
cp -r "$WORK/ansible-plugin/docs/images" "$P/docs/"
(cd "$WORK/ansible-plugin/src/main/resources/org/jenkinsci/plugins/ansible" &&
    find . -name 'help*.html' -exec cp --parents {} "$P/help/" \;)
html_to_md https://www.jenkins.io/doc/pipeline/steps/ansible/ "$P/jenkins-io-pipeline-steps-ansible.md"

echo "完成：$OUT"
cat "$SOURCES"
