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

Requirements: Java 1.8+, Eclipse Memory Analyzer 1.8.0+

TL;DR: use the following update repository to install the latest released version: https://dl.bintray.com/vlsi/eclipse/updates/

To install Calcite SQL plugin, perform the following:
1. Open `Help`, `Install New Software...`
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

Early access versions
---------------------

Development builds are pushed to https://dl.bintray.com/vlsi/eclipse-test/updates/ repository,
so you can preview the upcoming version right after the commit lands to the default branch.

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

 Fields are available as MAP.get call:

    select path -- retrieve field as usual
         , this['path'] -- retrieve field via MAP get
         , this['@className']
         , this['@class']['@classLoader'] -- nested calls work as well
      from java.net.URL

 The following virtual properties are available via MAP.get:

    @shallow     | shallow heap size of referenced object
    @retained    | retained heap size for referenced object
    @class       | the same as `getClass()` in Java
    @className   | class name (the same as `getClass().getName()` in Java)

 The following virtual properties are available for `Class` instances via MAP.get:

    @super       | super class
    @classLoader | returns `ClassLoader` for a given class

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
    getStringContent | pretty prints object representation

 The following table functions are supported:

    getRetainedSet(ref)        | returns the set of retained objects
    getOutboundReferences(ref) | returns outbound references (name, this) pairs
    getInboundReferences(ref)  | returns inbound references (this)
    getValues(ref)             | returns all values of a Java collection
    getMapEntries(ref)         | unnests Map as (key, value) tuples

 These functions can be called in a following way:
```sql
select
 u.this, refs.name, refs.this reference
from 
 java.net.URL u,
 lateral table(getOutboundReferences(u.this)) refs
```
 Another example:
```sql
select
 p.this, vals.key, vals."value"
from 
 java.util.Properties p,
 lateral table(getMapEntries(p.this)) vals
```

 The following collection functions are also supported:

    asMap(ref)                 | converts Java Map to SQL MAP, so it can be used like asMap(ref)['key']
    asMultiSet(ref)            | converts Java Collection to SQL MULTISET type

 These functions can be called in a following way:
```sql
select
 p.this
from 
 java.util.Properties p
where
 asMap(p.this)['java.vm.version'] is not null
```

 Another example:
```sql
select 
 fpc.this,
 fp.fp_ref
from 
 java.io.FilePermissionCollection fpc,
 unnest(asMultiSet(fpc.perms)) fp(fp_ref)
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

Commandline mode
----------------

You can process a single SQL via command line as follows

    ./MemoryAnalyzer -application MatCalcitePlugin.execute <heap-dump.file> <query.file> <result.file>

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
