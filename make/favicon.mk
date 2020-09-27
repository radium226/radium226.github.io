.PHONY: favicon
favicon: static/favicon.ico

static/favicon.ico:
	convert \
		"static/favicon.png" \
		-define icon:auto-resize="64,48,32,16" \
		favicon.ico