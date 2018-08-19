package com.github.vlsi.mat.calcite.editor;

import com.github.vlsi.mat.calcite.CalciteDataSource;
import org.apache.calcite.DataContext;
import org.apache.calcite.jdbc.CalciteConnection;
import org.apache.calcite.jdbc.CalciteMetaImpl;
import org.apache.calcite.sql.advise.SqlAdvisor;
import org.apache.calcite.sql.validate.SqlMoniker;
import org.apache.calcite.sql.validate.SqlMonikerType;
import org.apache.calcite.util.Util;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.*;
import org.eclipse.mat.query.IQueryContext;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.ui.editor.MultiPaneEditor;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CalciteContentAssistantProcessor implements IContentAssistProcessor {
    public static ISnapshot getSnapshot() {
        IWorkbenchPage page = PlatformUI.getWorkbench()
                .getActiveWorkbenchWindow().getActivePage();
        IEditorPart part = page == null ? null : page.getActiveEditor();

        if (!(part instanceof MultiPaneEditor)) {
            return null;
        }
        IQueryContext queryContext = ((MultiPaneEditor) part).getQueryContext();
        ISnapshot snapshot = (ISnapshot) queryContext.get(ISnapshot.class, null);
        return snapshot;
    }

    @Override
    public ICompletionProposal[] computeCompletionProposals(ITextViewer iTextViewer, int offset) {
        ISnapshot snapshot = getSnapshot();

        String sql = iTextViewer.getDocument().get();

        try (Connection con = CalciteDataSource.getConnection(snapshot)) {
            CalciteConnection ccon = con.unwrap(CalciteConnection.class);
            if (false) {
                // This is an official API
                System.out.println("advisor: ");
                try (PreparedStatement ps = con.prepareStatement("select id, names, type from table(getHints(?, ?)) as t")) {
                    ps.setString(1, sql);
                    ps.setInt(2, offset);
                    int cnt = 0;
                    try (ResultSet rs = ps.executeQuery()) {
                        System.out.println();
                        while (rs.next()) {
                            System.out.println("rs = " + rs.getString(1) + ", " + rs.getString(2) + ", " + rs.getString(3));
                            cnt++;
                        }
                    }
                    System.out.println(cnt + " items suggested");
                }
            }

            // This is unofficial API, however it enables more methods of SqlAdvisor
            final DataContext dataContext = CalciteMetaImpl.createDataContext(ccon);
            SqlAdvisor advisor = DataContext.Variable.SQL_ADVISOR.get(dataContext);

            String[] replaced = new String[1];
            List<SqlMoniker> completionHints = advisor.getCompletionHints(sql, offset, replaced);
            String replacement = replaced[0];

            List<SqlMoniker> hints = new ArrayList<>(completionHints.size());
            for (SqlMoniker hint : completionHints) {
                if (Util.last(hint.toIdentifier().names).startsWith("$")) {
                    continue;
                }
                hints.add(hint);
            }

            Collections.sort(hints, new Comparator<SqlMoniker>() {
                private int order(SqlMonikerType type) {
                    switch (type){
                        case CATALOG: return 0;
                        case SCHEMA: return 1;
                        case TABLE: return 2;
                        case VIEW: return 3;
                        case COLUMN: return 4;
                        case FUNCTION: return 5;
                        case KEYWORD: return 6;
                        case REPOSITORY: return 7;
                        default:
                            return 10;
                    }
                }

                @Override
                public int compare(SqlMoniker o1, SqlMoniker o2) {
                    int a = order(o1.getType());
                    int b = order(o2.getType());
                    if (a != b) {
                        return a < b ? -1 : 1;
                    }
                    return o1.id().compareTo(o2.id());
                }
            });

            ICompletionProposal[] res = new ICompletionProposal[hints.size()];
            for (int i = 0; i < hints.size(); i++) {
                SqlMoniker hint = hints.get(i);
                String hintStr = advisor.getReplacement(hint, replacement);
                res[i] = new CompletionProposal(hintStr, offset - replacement.length(),
                        replacement.length(), hintStr.length(),
                        null, hintStr + ", " + hint.getType().name(), null, "");
            }
            return res;
        } catch (SQLException e) {
            return null;
        }
    }

    @Override
    public IContextInformation[] computeContextInformation(ITextViewer iTextViewer, int i) {
        return new IContextInformation[0];
    }

    @Override
    public char[] getCompletionProposalAutoActivationCharacters() {
        return new char[]{'.', '"'};
    }

    @Override
    public char[] getContextInformationAutoActivationCharacters() {
        return new char[]{' ', '(', '.'};
    }

    @Override
    public String getErrorMessage() {
        return null;
    }

    @Override
    public IContextInformationValidator getContextInformationValidator() {
        return new ContextInformationValidator(this);
    }
}
