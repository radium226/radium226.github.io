.PHONY: site-generate
## Generate the site
site-generate: site/themes/devise/.git/config public/.git/config site/static/favicon.ico | articles-generate
	cd "site"
	# Generating static files
	hugo \
		--cleanDestinationDir \
		--destination "../public"

.PHONY: site-deploy
## Deploy the site
site-deploy: site-generate 
	git -C "public" add --all
	git -C "public" commit -m "$(shell git log -1 --pretty="%B")"
	git -C "public" push -u "origin" "public"

site/themes/devise/.git/config:
	mkdir -p "site/themes/devise"
	git clone \
		--single-branch \
		--branch "master" \
		"https://github.com/austingebauer/devise" \
		"themes/devise"

public/.git/config:
	mkdir -p "public"
	git clone \
		--single-branch \
		--branch "public" \
		$(shell git config --get "remote.origin.url") \
		"public"

site/static/favicon.ico:
	convert \
		"site/static/favicon.png" \
		-define icon:auto-resize="64,48,32,16" \
		"site/static/favicon.ico"