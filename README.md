Tesserae NG
============

This is Tesserae NG, an experimental ground-up rewrite of [Tesserae](http://tesserae.caset.buffalo.edu/) that uses [Solr](https://lucene.apache.org/solr/) for indexing.

Installing and running
----------------------

1. Download and install [VirtualBox](https://www.virtualbox.org/)
2. Download and install [Vagrant](http://www.vagrantup.com/)
3. Clone this repository somewhere
4. `cd tesserae-ng && vagrant up`

The initial run will need to download a virtual machine image, so be patient. Once the download is finished, the virtual machine will be booted, and tesserae-ng will be bootstrapped automatically.

Using
-----
Once the virtual machine has finished bootstrapping, simply visit `http://localhost:8000` to view the main webpage. The default install
has no texts, so everything needs to be ingested. You can log in with a username of 'admin' and a password of 'admin' to begin uploading
texts.

Texts
-----
Tesserae NG expects all uploaded texts to be in `.tess` format. If you want to get up and running quickly, visit the [Tesserae repository](https://github.com/tesserae/tesserae)
and see the `texts` directory.
