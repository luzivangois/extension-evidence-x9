import burp.IBurpExtender;
import burp.IBurpExtenderCallbacks;
import burp.IContextMenuFactory;
import burp.IContextMenuInvocation;
import burp.ITab;
import com.conviso.x9.ConvisoExtension;

import java.awt.Component;
import java.util.List;

/**
 * Burp loads extensions by the unqualified class name {@code BurpExtender} in
 * the default package, so this thin shim just forwards to the real
 * implementation in {@link ConvisoExtension}.
 */
public class BurpExtender implements IBurpExtender, IContextMenuFactory, ITab {

    private final ConvisoExtension delegate = new ConvisoExtension();

    @Override
    public void registerExtenderCallbacks(IBurpExtenderCallbacks callbacks) {
        delegate.registerExtenderCallbacks(callbacks);
    }

    @Override
    public List<javax.swing.JMenuItem> createMenuItems(IContextMenuInvocation invocation) {
        return delegate.createMenuItems(invocation);
    }

    @Override
    public String getTabCaption() {
        return delegate.getTabCaption();
    }

    @Override
    public Component getUiComponent() {
        return delegate.getUiComponent();
    }
}
