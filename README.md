Optiq MAT plugin
================

About
-----
This plugin for [Eclipse Memory Analyzer](http://www.eclipse.org/mat) allows to query heap dump via SQL

While MAT does have a query language, it does NOT allow to join, sort and group results.
Optiq MAT plugin allows all the typical SQL operations (joins, filters, group by, order by, etc)

Query engine is implemented via [Optiq](https://github.com/julianhyde/optiq)
See [Optiq SQL reference](https://github.com/julianhyde/optiq/blob/master/REFERENCE.md)

Sample
------

Query that lists duplicate URLs:

```sql
select file, count(*) cnt, sum(`@RETAINED`) sum_retained, sum(`@SHALLOW`) sum_shallow
  from `java.net.URL`
 group by file
having count(*)>1
 order by sum(`@RETAINED`) desc
```

To gen an explain plan, use `explain plan for select ...`:

```
EnumerableSortRel(sort0=[$2], dir0=[Descending])
  EnumerableCalcRel(expr#0..3=[{inputs}], expr#4=[1], expr#5=[>($t1, $t4)], proj#0..3=[{exprs}], $condition=[$t5])
    EnumerableAggregateRel(group=[{0}], cnt=[COUNT()], sum_retained=[SUM($1)], sum_shallow=[SUM($2)])
      EnumerableCalcRel(expr#0..14=[{inputs}], file=[$t11], @RETAINED=[$t2], @SHALLOW=[$t1])
        EnumerableTableAccessRel(table=[[HEAP, java.net.URL]])
```

Heap schema
-----------

### Each java class maps to a table. The table lists instances without subclasses
 For instance: `java.lang.Object`, `java.lang.String`
 Note: you need to use back-ticks to quote identifiers
 Note: it is assumed that classes sharing the same name have the same fields

 The fields become columns.

 The following special columns are added:

    @ID       | internal object identifier
    @SHALLOW  | shallow heap size of current instance
    @RETAINED | retained heap size of current instance

Requirements
------------
Java 1.7


Building
--------

Eclipse plugin cannot depend on jars from maven repository.
It has to be a OSGi bundle.
Currently we use maven-shade-plugin to create an uberjar, then use this jar in eclipse project

1. Build optiq-uberjar

    ```
    cd optiq-uberjar
    mvn package
    ```

    This will create a jar with all the dependencies in `optiq-uberjar/target` folder.

2. Import uberjar to MAT plugin project.

    Open eclipse project, import uberjar (right-click on project, import.., select jar, do not use link).
    
    Add uberjar at the dependencies tab of plugin in Eclipse.

3. Now you are ready to run the MAT with plugin. Just click "Launch an Eclipse application" in Eclipse.

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

Change log
----------
v1.0.1
  Updated optiq to 0.4.18: enable case-sensitive identifiers by default, back-tick quotes

v1.0.0
  Proof of concept.

Author
------
Vladimir Sitnikov <sitnikov.vladimir@gmail.com>
