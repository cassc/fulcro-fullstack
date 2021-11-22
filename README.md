# An demo application with fluro fullstack 


## Tests


With [Practicalli's deps.edn configuration](https://github.com/practicalli/clojure-deps-edn): 

```bash
clj -M:test/runner
```

## Development

### Develop in emacs

* In the project folder, run `yarn install` to pull Node.js dependencies.
* Run `cider-jack-in-clj&cljs`, select `shadow-cljs` followed by `shadow` and `:main` to start Clojure and ClojureScript REPLs.
* To start the backend server from REPL, run `(restart)` in `.src/dev/user.clj`.
* Open `http://localhost:3000` in browser to access the frontend website. It's recommended to install the [Fulcro Inspect Chrome extension](https://chrome.google.com/webstore/detail/fulcro-inspect/meeijplnfjcihnhkpanepcaffklobaal) for easier development.
* Open `http://localhost:9630/builds` in browser to access the Fulcro ClojureScript Dashboard


### Create runnable jar

To build a single uberjar file, run:

```bash
make build
```

## Note

> Since we have not added session, the login status won't survive page reload. 


## Setup clj-kondo 

For more information, https://github.com/clj-kondo/clj-kondo/blob/master/doc/config.md

```bash
mkdir .clj-kondo
clj-kondo --copy-configs --dependencies --lint "$(clojure -Spath)"
```
