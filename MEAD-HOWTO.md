# Intro
Mead is a Maven-based build system for Brew.  It works by building your
project via Maven, rendering a RPM spec file from a template, and then packaging
everything as an RPM with the rendered spec file.

## Main Builds
Building for the main branch for EL6 is available in Tito.

```
$ tito release mead
```

To build in EL6, you must adjust explicitly tell brew to create another wrapper
RPM using the tag you just build from.  For example, if I had just built
candlepin-2.0.4-1:

```
$ brew wrapper-rpm --create-build candlepin-mead-rhel-7-maven-candidate org.candlepin-candlepin-2.0.4-1 "git://git.app.eng.bos.redhat.com/candlepin.git?server#candlepin-2.0.4-1"
```

This additional wrapper-rpm step for EL7 is not automated in Tito (yet) because
it is reliant on the first build running successfully.

## Getting Started
Clone the RCM utility-scripts repository.  All the scripts you'll need are in
the `mead` directory.

## Maven Build
Mead will not just download JAR files willy-nilly.  You must first import them
into the buildroot.  You must have the `maven-import` and `regen-repo`
permissions in Brew.  You can see your permissions with `brew list-permissions
--mine`

To import just one artifact

```
$ cd $UTILITY_SCRIPTS_DIR
$ ./mead/get-maven-artifacts group:artifactId:packaging:version
$ ./mead/import-maven --tag YOUR_DEPENDENCIES_TAG ARTIFACT_FILES*
$ brew regen-repo YOUR_BUILDROOT
```

For example:

```
$ cd $UTILITY_SCRIPTS_DIR
$ ./mead/get-maven-artifacts org.apache:apache:pom:11
$ ./mead/import-maven --tag candlepin-mead-rhel-6-deps apache-11*
$ brew regen-repo candlepin-mead-rhel-6-build
```

However, I *never* import artifacts one at a time since so many of them have
other transitive dependencies.  You end up in stuck in is a cycle where you
import, build, hit a missing dependency, import it, build, hit a missing
dependency in the newly imported dependency, etc.  Instead, doing a local build
against a completely empty local repository allows you to avoid this scenario
since Maven will download (and therefore print a message in the build log)
everything that is needed.  You can then send that log to
`mead-load-build-dependencies` which will import everything that is missing.

The snippet below tells Maven to use an empty repository in `/tmp/m2_repo` and
then runs a Maven build.  All the output is teed to `/tmp/build.log`.  Since the
repository used is empty and some other Maven options are enabled, that log file
will contain the URL of every dependency the build requires.  Subsequently, go
to the checkout you made of the utility scripts git repository and run the
`mead-load-build-dependencies` script.  That script scrapes the log file of
the build and imports any missing dependencies into MEAD.  Finally, you need to
regenerate the repository meta-data.

```
$ cat > mead_settings.xml <<SETTINGS
<settings>
  <localRepository>/tmp/m2_repo/</localRepository>
</settings>
SETTINGS
$ mvn -B dependency:resolve-plugins deploy -Dmaven.test.skip=true -s mead_settings.xml -DaltDeploymentRepository=local-output::default::file:///tmp/output | tee /tmp/build.log
$ cd $UTILITY_SCRIPTS_DIR
$ ./mead/mead-load-build-dependencies --tag candlepin-mead-rhel-6-deps /tmp/build.log
$ brew regen-repo candlepin-mead-rhel-6-build
```

It is important to use an empty local repository so that all dependencies will
show up in the build log.  If you use a repository that already has some of
your dependencies, `mead-load-build-dependencies` won't pick them up (since
Maven doesn't download them) and you'll be stuck importing them one at a time.

## Scratch Builds
MEAD is set up to pull our source from a git repository rather than from a
tarball like rpmbuild.  If you are doing a scratch build, you don't want to push
your code to our official MEAD git repository.  Instead you need to create a git
repository somewhere else that Brew can access.  GitHub is not on the
whitelist, so the easiest solution is to set up a repository off of
git.engineering.redhat.com like so:

```
$ ssh KERB_ID@file.rdu.redhat.com
$ mkdir public_git
$ cd public_git
$ git clone --bare git://github.com/candlepin/candlepin
```

With the source available, you're ready to actually try a build using Brew's
`maven-build` or `maven-chain` subcommands.

## Building
The `maven-build` command is appropriate when you are building from the very
top of your source tree or if you have a very simple project.  With Candlepin,
however, you'll want to use `maven-chain` which gets around some of the
limitations of Maven.

Maven does not handle sibling dependencies well (if at all) such as the ones we
have with the code in the "common" project.  Building from the root of the
source tree would solve the problem, but would result in the final build
artifact being marked as "org.candlepin-candlepin-parent" (the project name in
the top level POM file) instead of as "gutterball" or "candlepin".  The solution
is to chain several MEAD builds together so that we can build the dependencies
we need.  Each shipped project has a `mead.chain` file.  The file is an INI
style with each section as a different link in the chain.  The section title is
in the "groupId-artifactId" format and contains a value for `scmurl` which is a
pointer to the SCM containing the code and a pointer to the git ref to use.  For
example

```
scmurl=git://example.com/candlepin?server#candlepin-2.0.0-1
```

Note that the SCM URL can be followed by a "?" and a subdirectory and that
the git ref is placed at the end after a "#".

Other options:

* `buildrequires` - a space delimited list of all sections that must be built
  beforehand
* `type` - set this to `wrapper` to add a wrapper RPM step.  Wrapper RPM steps
  need to have a `buildrequires` on the actual Maven build step.
* `packages` - a space delimited list of additional YUM packages to install
  before building
* `maven_options` - command line flags to pass to Maven
* `properties` - "key=value" list of properties to set with `-D`

Our `mead.chain` file is actually a simple Python template that is rendered
by Tito to alleviate the need for setting the git ref by hand.  If you need
to perform a manual build, you'll need to substitute in the values for the
SCM URL and git ref.

A scratch build is then triggered like so

```
$ brew maven-chain --scratch BREW_TARGET CHAIN_FILE
```

The `rhpkg` tool command for chain builds is similar if you are working in dist-git.

```
$ rhpkg maven-chain --target BREW_TARGET --ini CHAIN_FILE
```

## Spec File Template Rendering
Once a Maven build is complete, Mead can take a
[Cheetah](http://www.cheetahtemplate.org) of a RPM spec file use it to create
a wrapper RPM for the build.

A few special variables are made available to the Cheetah template.

* `$artifacts` - A hash of the Maven generated artifacts with the file
  extensions as keys.  For example: `{'.md5': 'my_project.jar.md5', '.jar':
  my_project.jar'}`
* `$all_artifacts` - All Maven generated artifacts in a list (including MD5
  sums, pom files, etc).
* `$all_artifacts_with_path` - All artifacts but with the full path to the
  artifact within the project.
* `$version` - The version of the **top level** project.
* `$release` - The release of the **top level** project.
* `$epoch` - The epoch of the **top level** project.

Mead can also access files that are in dist-git such as patches.  However, it
cannot access anything in the lookaside cache (which is where the source
tarballs are normally kept).

You can test building an RPM from a template by using an existing Mead build
with the `mead-test-spec-fragment` script.

```
$ ./mead-test-spec-fragment --task-id 123456 server/candlepin.spec.tmpl
```

`mead-test-spec-fragment` also has some options to just render the template
without attempting an RPM build.

Other Cheetah notes:

* Dollar signs are meaningful in Cheetah.  If your spec file contains shell
  variables, you will need to surround the relevant block with `#raw` and `#end
  raw` tags.

  ```
  #raw
  for selinuxvariant in %{selinux_variants}
  do
    make NAME=$selinuxvariant -f /usr/share/selinux/devel/Makefile
    mv %{modulename}.pp %{modulename}.pp.$selinuxvariant
    make NAME=$selinuxvariant -f /usr/share/selinux/devel/Makefile clean
  done
  #end raw
  ```
* Do not use line continuations (i.e. ending a line with '\')!
  Cheetah doesn't like them for some reason.
* Do not use non-ASCII characters anywhere in the Cheetah template (including
  names with non-ASCII characters in the changelog.)  I ran into difficulties
  with the template failing to render when non-ASCII characters appeared in it.
* Always surround the entire `%changelog` section in `#raw` and `#end raw` tags
  so that people's changelog entries won't break the template.
