[![Build Status](https://travis-ci.org/vlsi/mat-calcite-plugin.svg?branch=master)](https://travis-ci.org/vlsi/mat-calcite-plugin)

MAT Calcite plugin
==================

About
-----
This plugin for [Eclipse Memory Analyzer](http://www.eclipse.org/mat) allows to query heap dump via SQL

While MAT does have a query language, it does NOT allow to join, sort and group results.
MAT Calcite plugin allows all the typical SQL operations (joins, filters, group by, order by, etc)

Query engine is implemented via [Apache Calcite](https://calcite.apache.org)
See [Calcite SQL reference](https://calcite.apache.org/docs/reference.html)

Installation
------------

**Note for Memory Analyzer 1.7.0**: you must disable "Eclipse Oxygen" update site before installing plugins
otherwise your MAT installation will get broken.

To disable Eclipse Oxygen in MAT 1.7.0, perform the following:
1. Open MAT Settings
1. Open `Install/Update`, `Available Software Sites`
1. Uncheck `Eclipse Oxygen`
1. Click `Ok`

TL;DR: use the following update repository to install the latest released version: https://dl.bintray.com/vlsi/eclipse/updates/

To install Calcite SQL plugin, perform the following:
1. Open `Help`, `Install New Software...`
1. **For MAT 1.7.0**: in case you have not disabled Eclipse Oxygen update site, click `Available Software Sites` and disable Oxygen there
1. Click `Add`, it will open a `Add Repository` window
1. Type `Calcite SQL plugin site` to the `Name` field
1. Type `https://dl.bintray.com/vlsi/eclipse/updates/` to the `Location` field
1. Click `Ok`
1. All the checkboxes can be left by default (`Show only latest version`, `Group items by category`, ...)
1. Check `SQL for Memory Analyzer` category
1. Click `Next` (Available Software)
1. Click `Next` (Installation Details)
1. Accept License
1. Click `Finish` and restart MAT

The following update site can be used to get development builds: https://dl.bintray.com/vlsi/eclipse-test/updates/

Sample
------

Query that lists duplicate URLs:

```sql
select toString(file) file_str, count(*) cnt, sum(retainedSize(this)) sum_retained, sum(shallowSize(this)) sum_shallow
  from java.net.URL
 group by toString(file)
having count(*)>1
 order by sum(retainedSize(this)) desc
```

To get an explain plan, use "explain plan for select ...":

```
EnumerableSort(sort0=[$2], dir0=[DESC])
  View (expr#0..3=[{inputs}], expr#4=[1], expr#5=[>($t1, $t4)], proj#0..3=[{exprs}], $condition=[$t5])
    EnumerableAggregate(group=[{0}], cnt=[COUNT()], sum_retained=[$SUM0($1)], sum_shallow=[$SUM0($2)])
      View (expr#0=[{inputs}], expr#1=[0], expr#2=[GET_SNAPSHOT($t1)], expr#3=[GET_IOBJECT($t2, $t0)], expr#4=['file'], expr#5=[RESOLVE_REFERENCE($t3, $t4)], expr#6=[toString($t5)], expr#7=[TO_REFERENCE($t3)], expr#8=[retainedSize($t7)], expr#9=[shallowSize($t7)], file=[$t6], $f1=[$t8], $f2=[$t9])
        GetObjectIdsByClass (class=java.net.URL)
```

Join sample
-----------

```sql
 select u.this, retainedSize(s.this)
   from "java.lang.String" s
   join "java.net.URL" u
     on s.this = u.path
```

Here's execution plan:

```
View (expr#0..2=[{inputs}], expr#3=[retainedSize($t2)], this=[$t0], EXPR$1=[$t3])
  HashJoin (condition=[=($1, $2)], joinType=[inner])
    View (expr#0=[{inputs}], expr#1=[0], expr#2=[GET_SNAPSHOT($t1)], expr#3=[GET_IOBJECT($t2, $t0)], expr#4=[TO_REFERENCE($t3)], expr#5=['path'], expr#6=[RESOLVE_REFERENCE($t3, $t5)], this=[$t4], path=[$t6])
      GetObjectIdsByClass (class=java.net.URL)
    View (expr#0=[{inputs}], expr#1=[0], expr#2=[GET_SNAPSHOT($t1)], expr#3=[GET_IOBJECT($t2, $t0)], expr#4=[TO_REFERENCE($t3)], this=[$t4])
      GetObjectIdsByClass (class=java.lang.String)
```

Heap schema
-----------

    heap (default schema)
    +- java (sub-schema name)
       +- util (sub-schema name)
          +- HashMap (table name).
             This "table" would return all the instances of java.util.HashMap without subclasses
    +- instanceof
       +- java
          +- util
             +- HashMap (table name).
                This would return HashMap instances as well as subclass instances (e.g. LinkedHashMap)
    +- "java.util.HashMap" (table name)
       This can be used as alternative.
    +- native.ThreadStackFrames (table name)
       Returns thread stack traces and local variable info

### Each java class maps to a table. The table lists instances without subclasses
 For instance: "java.lang.Object", "java.lang.String"
 Note: you need to use double quotes to quote identifiers
 Note: it is assumed that classes sharing the same name have the same fields

 The fields become columns.

 The following special columns are added:

    this         | reference to current object

 The following functions can be used to work with column which represents reference:

    getId        | internal object identifier for referenced object
    getAddress   | memory address for referenced object
    getType      | class name of referenced object
    toString     | textual representation of referenced object
    shallowSize  | shallow heap size of referenced object
    retainedSize | retained heap size for referenced object
    length       | length of referenced array
    getSize      | size of referenced collection, map or count of non-null elements in array
    getByKey     | extracts value for given string representation of key for referenced map
    getField     | obtains value of field with specified name for referenced object

 The following table functions are supported

    getValues(ref)             | returns all values of a Java collection
    getRetainedSet(ref)        | returns the set of retained objects
    getOutboundReferences(ref) | returns outbound references (name, this) pairs
    getInboundReferences(ref)  | returns inbound references (this)

 `CROSS APPLY` and `OUTER APPLY` might be used to call table functions:

```sql
select u.this, refs.name, refs.this reference
  from java.net.URL u
 cross apply table(getOutboundReferences(u.this)) refs
```

Requirements
------------
Java 1.8 as a build JDK. The code should still be Java 1.7 compatible.
Memory Analyzer Tool 1.5 or higher


Building
--------

Eclipse plugin cannot depend on jars from maven repository.
It has to be a OSGi bundle, however Calcite is easier to reach via maven.
So we use two-phase approach: bundle the dependencies in a single jar, then use this jar in eclipse project.

1. Build dependencies.jar

    ```
    cd MatCalciteDependencies
    mvn install
    ```

    This will create a jar with all the dependencies in `dependencies/target` folder.
    You do not need to touch/move/copy the jar.

2. Build the plugin

    ```
    mvn install # from the top-level folder
    ```

    Note: this will copy `MatCalciteDependencies` to `MatCalcitePlugin/MatCalcitePlugin/target/dependency` so Eclipse can find it.

    The final repository (aka "update site") with the plugin will be created in `eclipse-repository/target/eclipse-repository-1.0.0-SNAPSHOT.zip`


Running
-------

It is not yet clear how to run the plugin via maven.
To launch via Eclipse, just open Eclipse project, double-click `plugin.xml`, then click `Launch an Eclipse application`.

Roadmap
-------

- enhance heap schema
- context menu support
- heap-related filtering operators (instance of, dominator of, etc)
- interoperation with mat queries (histogram, retained set, etc)
- support external data providers (e.g. allow to join heapdump objects with csv file)
- projection support (do not compute unnecessary columns)
- optimizer rules

License
-------
This library is distributed under terms of Apache 2 License

Author
------
Vladimir Sitnikov <sitnikov.vladimir@gmail.com>
