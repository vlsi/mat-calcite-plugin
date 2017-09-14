package com.github.vlsi.mat.calcite.editor;

import com.github.vlsi.mat.calcite.CalciteDataSource;
import org.apache.calcite.DataContext;
import org.apache.calcite.jdbc.CalciteConnection;
import org.apache.calcite.jdbc.CalciteMetaImpl;
import org.apache.calcite.sql.advise.SqlAdvisor;
import org.apache.calcite.sql.validate.SqlMoniker;
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
import java.util.List;

public class CalciteContentAssistantProcessor implements IContentAssistProcessor {
    @Override
    public ICompletionProposal[] computeCompletionProposals(ITextViewer iTextViewer, int offset) {
        IWorkbenchPage page = PlatformUI.getWorkbench()
                .getActiveWorkbenchWindow().getActivePage();
        IEditorPart part = page == null ? null : page.getActiveEditor();

        if (!(part instanceof MultiPaneEditor)) {
            return null;
        }
        IQueryContext queryContext = ((MultiPaneEditor) part).getQueryContext();
        ISnapshot snapshot = (ISnapshot) queryContext.get(ISnapshot.class, null);

        String sql = iTextViewer.getDocument().get();

        try (Connection con = CalciteDataSource.getConnection(snapshot)) {
            CalciteConnection ccon = con.unwrap(CalciteConnection.class);
            String defaultSchema = ccon.getSchema();
            if (false) {
                // This is an official API
                System.out.println("advisor: ");
                try (PreparedStatement ps = con.prepareStatement("select * from table(getHints(?, ?)) as t(id, names, type)")) {
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

            ICompletionProposal[] res = new ICompletionProposal[hints.size()];
            System.out.println();
            for (int i = 0; i < hints.size(); i++) {
                SqlMoniker hint = hints.get(i);
                List<String> qualifiedNames = hint.getFullyQualifiedNames();
                if (defaultSchema.equals(qualifiedNames.get(0))) {
                    qualifiedNames = Util.skip(qualifiedNames);
                }
                String hintStr = Util.sepList(qualifiedNames, ".");
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
