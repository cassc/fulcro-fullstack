clean:
	rm -rf target node_modules resources/public/js/main/
build:
	yarn install
	clj -M:cljs/prod
	clj -X:uberjar
