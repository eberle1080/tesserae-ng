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

Hacking
-----
If you want to modify the source code, go ahead and work on your local box (the one where you initially cloned the git repo). Once you're done, log in to the (presumably running) box:

    $> ssh -p 2222 tesserae@localhost

The password is `tesserae` Now run the refresh script (in the `tesserae` user's home directory):

    $> ./refresh.sh

You'll be prompted for your password. Again, the password is `tesserae` This will update all of the required source files and configuration files and install them in their proper places. You can now visit `localhost:8000` to see your changes.

If you're hacking on the Solr code, you can visit the Solr console here: `http://localhost:8080/solr`
