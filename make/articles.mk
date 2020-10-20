site/content/articles/postgresql-changes.md: repos/postgresql-changes/.git/config
	git -C "repos/postgresql-changes" pull
	cp "repos/postgresql-changes/article.md" "content/articles/postgresql-changes.md"


repos/postgresql-changes/.git/config:
	mkdir -p "repos/postgresql-changes"
	git clone \
		"git@github.com:radium226/postgresql-changes-scala.git" \
		"repos/postgresql-changes"


.PHONY: articles-generate
articles-generate: site/content/articles/postgresql-changes.md

.PHONY: articles-clean
articles-clean:
	if [[ ! -L "site/content/articles/posgresql-changes.md" ]]; then
		rm -Rf "site/content/articles/posgresql-changes.md"
	fi