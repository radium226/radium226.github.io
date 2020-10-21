site/content/articles/postgresql-changes.md: repos/postgresql-changes/.git/config $(INCLUDE_SNIPPETS)
	git -C "repos/postgresql-changes" pull
	$(INCLUDE_SNIPPETS) \
		-s "repos/postgresql-changes" \
		"repos/postgresql-changes/article.md" \
			>"site/content/articles/postgresql-changes.md"


repos/postgresql-changes/.git/config:
	mkdir -p "repos/postgresql-changes"
	git clone \
		"git@github.com:radium226/postgresql-changes-scala.git" \
		"repos/postgresql-changes"


.PHONY: articles-generate
## Generate articles
articles-generate: site/content/articles/postgresql-changes.md

.PHONY: articles-clean
## Clean articles
articles-clean:
	if [[ ! -L "site/content/articles/posgresql-changes.md" ]]; then
		rm -Rf "site/content/articles/posgresql-changes.md"
	fi