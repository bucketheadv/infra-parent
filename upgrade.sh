#!/bin/bash

set -e

# 获取根项目版本
ROOT_VERSION=$(sed -n '/<project/,/<\/project>/p' pom.xml \
  | sed '/<parent/,/<\/parent>/d' \
  | grep -m 1 '<version>' \
  | sed -E 's/.*<version>([^<]+)<\/version>.*/\1/')

if [ -z "$ROOT_VERSION" ]; then
  echo "❌ Failed to extract root version from pom.xml"
  exit 1
fi

echo "📦 Root version: $ROOT_VERSION"

# 查找所有子模块中的 pom.xml 文件（排除根 pom.xml）
find . -mindepth 2 -name pom.xml | while read -r POM_FILE; do
  echo "🔧 Updating $POM_FILE"

  # 修改 <parent><version> 中的值为 ROOT_VERSION
  sed -i.bak -E "/<parent>/,/<\/parent>/ s|(<version>)[^<]+(</version>)|\1$ROOT_VERSION\2|" "$POM_FILE"

  rm "$POM_FILE.bak"
done

echo "✅ Done updating parent versions."
