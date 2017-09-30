# Gogolica

<img src="https://upload.wikimedia.org/wikipedia/commons/d/df/N.Gogol_by_A.Ivanov_%281841%2C_Russian_museum%29.jpg"
title="Nikolai Vasilievich Gogol" align="right" padding="5px" height="250px">

> Always think of what is useful and not what is beautiful.   
> Beauty will come of its own accord.
>
> *Nikolai Vasilievich Gogol*

> You can't imagine how stupid the whole world has grown nowadays.
>
> *Also Nikolai Vasilievich Gogol*

<br clear=all /><br />

***Warning: ALPHA STATUS***

Gogolica is an auto-generated Clojure bindings library for Google APIs.

The implemented APIs are the ones provided from the Google Discovery Service's 
JSON description files of the available "new style" Google APIs.

The generator itself and the code it produces are *Alpha*.

Some APIs are alpha/beta, and indicated as such in the namespace 
(e.g., "gogolica.storage.v1alpha").

## Available APIs

None yet.

## Usage

### `gogolica.storage.v1`

```clojure
(require '[gogolica.storage.v1 :as gcs])

;; Authentication, if you have the env variable GOOGLE_APPLICATION_DEFAULT set,
;; then your service account key will be read from the path specified in it.
;; Otherwise you can load it manually:
(gogolica.core.auth/key-from-file "path/to/key.json")

;; List buckets for your project
(gcs/buckets-list "my-project-name" {})

;; List objects for a buckets
(gcs/objects-list "my-bucket-name" {})

;; Create new bucket
(gcs/buckets-insert {:name "my-new-bucket-name"} "my-project-name" {})

;; Upload a new object
(gcs/objects-insert "/absolute/path/to/file.png" {} "my-new-bucket-name" {:name "my-file-name"})

;; Download the new object - metadata
(gcs/objects-get "my-new-bucket-name" "my-file-name" {})

;; Download the new object - data as bytearray
(gcs/objects-get "my-new-bucket-name" "my-file-name" {:alt "media"})
```

## Developing

### Getting the JSON models for Google APIs

This will clone the Google auto-generated Go library in the `vendor` directory,
and copy over their versioned json models to the `model` folder.

```bash
./script/copy-models
```

### TODO

> TODO

## Testing

### Unit tests (test pure functions)

```bash
lein test
```

### Integration tests (test hitting APIs)

> TODO

## License

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
