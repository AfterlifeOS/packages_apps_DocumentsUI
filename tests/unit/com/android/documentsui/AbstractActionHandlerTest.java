/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.documentsui;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import android.content.Intent;
import android.net.Uri;
import android.os.Parcelable;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Path;

import androidx.recyclerview.selection.ItemDetailsLookup.ItemDetails;
import androidx.test.filters.MediumTest;

import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.EventListener;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.Shared;
import com.android.documentsui.base.State;
import com.android.documentsui.files.LauncherActivity;
import com.android.documentsui.sorting.SortDimension;
import com.android.documentsui.sorting.SortModel;
import com.android.documentsui.testing.DocumentStackAsserts;
import com.android.documentsui.testing.Roots;
import com.android.documentsui.testing.TestEnv;
import com.android.documentsui.testing.TestEventHandler;
import com.android.documentsui.testing.TestProvidersAccess;
import com.android.documentsui.testing.UserManagers;
import com.android.modules.utils.build.SdkLevel;

import com.google.android.collect.Lists;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A unit test *for* AbstractActionHandler, not an abstract test baseclass.
 */
@RunWith(Parameterized.class)
@MediumTest
public class AbstractActionHandlerTest {

    private final TestConfigStore mTestConfigStore = new TestConfigStore();
    private TestActivity mActivity;
    private TestEnv mEnv;
    private AbstractActionHandler<TestActivity> mHandler;

    @Parameter(0)
    public boolean isPrivateSpaceEnabled;

    /**
     * Parametrize values for {@code isPrivateSpaceEnabled} to run all the tests twice once with
     * private space flag enabled and once with it disabled.
     */
    @Parameters(name = "privateSpaceEnabled={0}")
    public static Iterable<?> data() {
        return Lists.newArrayList(true, false);
    }

    @Before
    public void setUp() {
        mEnv = TestEnv.create();
        mActivity = TestActivity.create(mEnv);
        mActivity.userManager = UserManagers.create();
        mEnv.state.configStore = mTestConfigStore;

        isPrivateSpaceEnabled = SdkLevel.isAtLeastS() && isPrivateSpaceEnabled;
        if (isPrivateSpaceEnabled) {
            mTestConfigStore.enablePrivateSpaceInPhotoPicker();
            mEnv.state.canForwardToProfileIdMap.put(TestProvidersAccess.USER_ID, true);
        }
        mHandler = new AbstractActionHandler<TestActivity>(
                mActivity,
                mEnv.state,
                mEnv.providers,
                mEnv.docs,
                mEnv.searchViewManager,
                mEnv::lookupExecutor,
                mEnv.injector) {

            @Override
            public void openRoot(RootInfo root) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean openItem(
                    ItemDetails<String> doc, @ViewType int type, @ViewType int fallback) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void initLocation(Intent intent) {
                throw new UnsupportedOperationException();
            }

            @Override
            protected void launchToDefaultLocation() {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Test
    public void testOpenNewWindow() {
        DocumentStack path = new DocumentStack(Roots.create("123"));
        mHandler.openInNewWindow(path);

        Intent expected = LauncherActivity.createLaunchIntent(mActivity);
        expected.putExtra(Shared.EXTRA_STACK, (Parcelable) path);
        Intent actual = mActivity.startActivity.getLastValue();
        assertEquals(expected.toString(), actual.toString());
    }

    @Test
    public void testOpensContainerDocuments_OpenFolderInSearch_JumpsToNewLocation()
            throws Exception {
        if (!mEnv.features.isLaunchToDocumentEnabled()) {
            return;
        }

        mEnv.populateStack();

        mEnv.searchViewManager.isSearching = true;
        mEnv.docs.nextIsDocumentsUri = true;
        mEnv.docs.nextPath = new Path(
                TestProvidersAccess.HOME.rootId,
                Arrays.asList(TestEnv.FOLDER_1.documentId, TestEnv.FOLDER_2.documentId));
        mEnv.docs.nextDocuments = Arrays.asList(TestEnv.FOLDER_1, TestEnv.FOLDER_2);

        mHandler.openContainerDocument(TestEnv.FOLDER_2);

        mEnv.beforeAsserts();

        assertEquals(mEnv.docs.nextPath.getPath().size(), mEnv.state.stack.size());
        assertEquals(TestEnv.FOLDER_2, mEnv.state.stack.pop());
        assertEquals(TestEnv.FOLDER_1, mEnv.state.stack.pop());
    }


    @Test
    public void testOpensContainerDocuments_ClickFolderInSearch_PushToRootDoc_NoFindPathSupport()
            throws Exception {
        mEnv.populateStack();

        mEnv.searchViewManager.isSearching = true;
        mEnv.docs.nextIsDocumentsUri = true;
        mEnv.docs.nextDocuments = Arrays.asList(TestEnv.FOLDER_1, TestEnv.FOLDER_2);

        mHandler.openContainerDocument(TestEnv.FOLDER_2);

        mEnv.beforeAsserts();

        assertEquals(2, mEnv.state.stack.size());
        assertEquals(TestEnv.FOLDER_2, mEnv.state.stack.pop());
        assertEquals(TestEnv.FOLDER_0, mEnv.state.stack.pop());
    }

    @Test
    public void testOpensContainerDocuments_ClickArchiveInSearch_opensArchiveInArchiveProvider()
            throws Exception {
        if (!mEnv.features.isLaunchToDocumentEnabled()) {
            return;
        }

        mEnv.populateStack();

        mEnv.searchViewManager.isSearching = true;
        mEnv.docs.nextIsDocumentsUri = true;
        mEnv.docs.nextPath = new Path(
                TestProvidersAccess.HOME.rootId,
                Arrays.asList(TestEnv.FOLDER_1.documentId, TestEnv.FOLDER_2.documentId,
                        TestEnv.FILE_ARCHIVE.documentId));
        mEnv.docs.nextDocuments = Arrays.asList(
                TestEnv.FOLDER_1, TestEnv.FOLDER_2, TestEnv.FILE_ARCHIVE);
        mEnv.docs.nextDocument = TestEnv.FILE_IN_ARCHIVE;

        mHandler.openContainerDocument(TestEnv.FILE_ARCHIVE);

        mEnv.beforeAsserts();

        assertEquals(mEnv.docs.nextPath.getPath().size(), mEnv.state.stack.size());
        assertEquals(TestEnv.FILE_IN_ARCHIVE, mEnv.state.stack.pop());
        assertEquals(TestEnv.FOLDER_2, mEnv.state.stack.pop());
        assertEquals(TestEnv.FOLDER_1, mEnv.state.stack.pop());
    }

    @Test
    public void testOpensDocument_ExceptionIfAlreadyInStack() throws Exception {
        mEnv.populateStack();
        try {
            mEnv.state.stack.push(TestEnv.FOLDER_0);
            fail("Should have thrown IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testLaunchToDocuments() throws Exception {
        if (!mEnv.features.isLaunchToDocumentEnabled()) {
            return;
        }

        mEnv.docs.nextIsDocumentsUri = true;
        mEnv.docs.nextPath = new Path(
                TestProvidersAccess.HOME.rootId,
                Arrays.asList(
                        TestEnv.FOLDER_0.documentId,
                        TestEnv.FOLDER_1.documentId,
                        TestEnv.FILE_GIF.documentId));
        mEnv.docs.nextDocuments =
                Arrays.asList(TestEnv.FOLDER_0, TestEnv.FOLDER_1, TestEnv.FILE_GIF);

        mActivity.refreshCurrentRootAndDirectory.assertNotCalled();
        assertTrue(mHandler.launchToDocument(TestEnv.FILE_GIF.derivedUri));

        mEnv.beforeAsserts();

        DocumentStackAsserts.assertEqualsTo(mEnv.state.stack, TestProvidersAccess.HOME,
                Arrays.asList(TestEnv.FOLDER_0, TestEnv.FOLDER_1));
        mActivity.refreshCurrentRootAndDirectory.assertCalled();
    }

    @Test
    public void testLaunchToDocuments_convertsTreeUriToDocumentUri() throws Exception {
        if (!mEnv.features.isLaunchToDocumentEnabled()) {
            return;
        }

        mEnv.docs.nextIsDocumentsUri = true;
        mEnv.docs.nextPath = new Path(
                TestProvidersAccess.HOME.rootId,
                Arrays.asList(
                        TestEnv.FOLDER_0.documentId,
                        TestEnv.FOLDER_1.documentId,
                        TestEnv.FILE_GIF.documentId));
        mEnv.docs.nextDocuments =
                Arrays.asList(TestEnv.FOLDER_0, TestEnv.FOLDER_1, TestEnv.FILE_GIF);

        final Uri treeBaseUri = DocumentsContract.buildTreeDocumentUri(
                TestProvidersAccess.HOME.authority, TestEnv.FOLDER_0.documentId);
        final Uri treeDocUri = DocumentsContract.buildDocumentUriUsingTree(
                treeBaseUri, TestEnv.FILE_GIF.documentId);
        assertTrue(mHandler.launchToDocument(treeDocUri));

        mEnv.beforeAsserts();

        DocumentStackAsserts.assertEqualsTo(mEnv.state.stack, TestProvidersAccess.HOME,
                Arrays.asList(TestEnv.FOLDER_0, TestEnv.FOLDER_1));
        mEnv.docs.lastUri.assertLastArgument(TestEnv.FILE_GIF.derivedUri);
        mActivity.refreshCurrentRootAndDirectory.assertCalled();
    }

    @Test
    public void testLoadChildrenDocuments() throws Exception {
        mEnv.state.stack.changeRoot(TestProvidersAccess.HOME);
        mEnv.state.stack.push(TestEnv.FOLDER_0);

        mEnv.state.sortModel.sortByUser(
                SortModel.SORT_DIMENSION_ID_TITLE, SortDimension.SORT_DIRECTION_ASCENDING);

        mEnv.mockProviders.get(TestProvidersAccess.HOME.authority)
                .setNextChildDocumentsReturns(TestEnv.FILE_APK, TestEnv.FILE_GIF);

        mHandler.loadDocumentsForCurrentStack();
        CountDownLatch latch = new CountDownLatch(1);
        mEnv.model.addUpdateListener(event -> latch.countDown());
        mActivity.supportLoaderManager.runAsyncTaskLoader(AbstractActionHandler.LOADER_ID);

        latch.await(1, TimeUnit.SECONDS);
        assertEquals(2, mEnv.model.getItemCount());
        String[] modelIds = mEnv.model.getModelIds();
        assertEquals(TestEnv.FILE_APK, mEnv.model.getDocument(modelIds[0]));
        assertEquals(TestEnv.FILE_GIF, mEnv.model.getDocument(modelIds[1]));
    }

    @Test
    public void testCrossProfileDocuments_success() throws Exception {
        mEnv.state.action = State.ACTION_GET_CONTENT;
        if (isPrivateSpaceEnabled) {
            mEnv.state.canForwardToProfileIdMap.put(TestProvidersAccess.OtherUser.USER_ID, true);
        } else {
            mEnv.state.canShareAcrossProfile = true;
        }
        mEnv.state.stack.changeRoot(TestProvidersAccess.OtherUser.HOME);
        mEnv.state.stack.push(TestEnv.OtherUser.FOLDER_0);

        mEnv.state.sortModel.sortByUser(
                SortModel.SORT_DIMENSION_ID_TITLE, SortDimension.SORT_DIRECTION_ASCENDING);

        // Currently mock provider does not have cross profile concept, this will always return
        // the supplied docs without checking for the user. But this should not be a problem for
        // this test case.
        mEnv.mockProviders.get(TestProvidersAccess.OtherUser.HOME.authority)
                .setNextChildDocumentsReturns(TestEnv.OtherUser.FILE_PNG);

        mHandler.loadDocumentsForCurrentStack();
        CountDownLatch latch = new CountDownLatch(1);
        mEnv.model.addUpdateListener(event -> latch.countDown());
        mActivity.supportLoaderManager.runAsyncTaskLoader(AbstractActionHandler.LOADER_ID);

        latch.await(1, TimeUnit.SECONDS);
        assertEquals(1, mEnv.model.getItemCount());
        String[] modelIds = mEnv.model.getModelIds();
        assertEquals(TestEnv.OtherUser.FILE_PNG, mEnv.model.getDocument(modelIds[0]));
    }

    @Test
    public void testLoadCrossProfileDoc_failsWithQuietModeException() throws Exception {
        mEnv.state.action = State.ACTION_GET_CONTENT;
        if (isPrivateSpaceEnabled) {
            mEnv.state.canForwardToProfileIdMap.put(TestProvidersAccess.OtherUser.USER_ID, true);
        } else {
            mEnv.state.canShareAcrossProfile = true;
        }
        mEnv.state.stack.changeRoot(TestProvidersAccess.OtherUser.HOME);
        mEnv.state.stack.push(TestEnv.OtherUser.FOLDER_0);
        // Turn off the other user.
        when(mActivity.userManager.isQuietModeEnabled(TestProvidersAccess.OtherUser.USER_HANDLE))
                .thenReturn(true);

        TestEventHandler<Model.Update> listener = new TestEventHandler<>();
        mEnv.model.addUpdateListener(listener::accept);

        mHandler.loadDocumentsForCurrentStack();
        CountDownLatch latch = new CountDownLatch(1);
        mEnv.model.addUpdateListener(event -> latch.countDown());
        mActivity.supportLoaderManager.runAsyncTaskLoader(AbstractActionHandler.LOADER_ID);

        latch.await(1, TimeUnit.SECONDS);
        assertThat(listener.getLastValue().getException())
                .isInstanceOf(CrossProfileQuietModeException.class);
    }

    @Test
    public void testLoadCrossProfileDoc_failsWithNoPermissionException() throws Exception {
        mEnv.state.action = State.ACTION_GET_CONTENT;
        mEnv.state.stack.changeRoot(TestProvidersAccess.OtherUser.HOME);
        mEnv.state.stack.push(TestEnv.OtherUser.FOLDER_0);
        // Disallow sharing across profile
        mEnv.state.canShareAcrossProfile = false;

        TestEventHandler<Model.Update> listener = new TestEventHandler<>();
        mEnv.model.addUpdateListener(listener::accept);

        mHandler.loadDocumentsForCurrentStack();
        CountDownLatch latch = new CountDownLatch(1);
        mEnv.model.addUpdateListener(event -> latch.countDown());
        mActivity.supportLoaderManager.runAsyncTaskLoader(AbstractActionHandler.LOADER_ID);

        latch.await(1, TimeUnit.SECONDS);
        assertThat(listener.getLastValue().getException())
                .isInstanceOf(CrossProfileNoPermissionException.class);
    }

    @Test
    public void testLoadCrossProfileDoc_bothError_showNoPermissionException() throws Exception {
        mEnv.state.action = State.ACTION_GET_CONTENT;
        mEnv.state.stack.changeRoot(TestProvidersAccess.OtherUser.HOME);
        mEnv.state.stack.push(TestEnv.OtherUser.FOLDER_0);
        // Disallow sharing
        mEnv.state.canShareAcrossProfile = false;
        // Turn off the other user.
        when(mActivity.userManager.isQuietModeEnabled(TestProvidersAccess.OtherUser.USER_HANDLE))
                .thenReturn(true);

        TestEventHandler<Model.Update> listener = new TestEventHandler<>();
        mEnv.model.addUpdateListener(listener::accept);

        mHandler.loadDocumentsForCurrentStack();
        CountDownLatch latch = new CountDownLatch(1);
        mEnv.model.addUpdateListener(event -> latch.countDown());
        mActivity.supportLoaderManager.runAsyncTaskLoader(AbstractActionHandler.LOADER_ID);

        latch.await(1, TimeUnit.SECONDS);
        assertThat(listener.getLastValue().getException())
                .isInstanceOf(CrossProfileNoPermissionException.class);
    }

    @Test
    public void testCrossProfileDocuments_reloadSuccessAfterCrossProfileError() throws Exception {
        mEnv.state.action = State.ACTION_GET_CONTENT;
        mEnv.state.stack.changeRoot(TestProvidersAccess.OtherUser.HOME);
        mEnv.state.stack.push(TestEnv.OtherUser.FOLDER_0);

        mEnv.state.sortModel.sortByUser(
                SortModel.SORT_DIMENSION_ID_TITLE, SortDimension.SORT_DIRECTION_ASCENDING);

        // Currently mock provider does not have cross profile concept, this will always return
        // the supplied docs without checking for the user. But this should not be a problem for
        // this test case.
        mEnv.mockProviders.get(TestProvidersAccess.OtherUser.HOME.authority)
                .setNextChildDocumentsReturns(TestEnv.OtherUser.FILE_PNG);

        // Disallow sharing across profile
        if (isPrivateSpaceEnabled) {
            mEnv.state.canForwardToProfileIdMap.put(TestProvidersAccess.OtherUser.USER_ID, false);
        } else {
            mEnv.state.canShareAcrossProfile = false;
        }

        TestEventHandler<Model.Update> listener = new TestEventHandler<>();
        mEnv.model.addUpdateListener(listener::accept);

        mHandler.loadDocumentsForCurrentStack();
        CountDownLatch latch1 = new CountDownLatch(1);
        EventListener<Model.Update> updateEventListener1 = update -> latch1.countDown();
        mEnv.model.addUpdateListener(updateEventListener1);
        mActivity.supportLoaderManager.runAsyncTaskLoader(AbstractActionHandler.LOADER_ID);
        latch1.await(1, TimeUnit.SECONDS);
        assertThat(listener.getLastValue().getException())
                .isInstanceOf(CrossProfileNoPermissionException.class);

        // Allow sharing across profile.
        if (isPrivateSpaceEnabled) {
            mEnv.state.canForwardToProfileIdMap.put(TestProvidersAccess.OtherUser.USER_ID, true);
        } else {
            mEnv.state.canShareAcrossProfile = true;
        }

        CountDownLatch latch2 = new CountDownLatch(1);
        mEnv.model.addUpdateListener(update -> latch2.countDown());
        mHandler.loadDocumentsForCurrentStack();
        mActivity.supportLoaderManager.runAsyncTaskLoader(AbstractActionHandler.LOADER_ID);

        latch2.await(1, TimeUnit.SECONDS);
        assertEquals(1, mEnv.model.getItemCount());
        String[] modelIds = mEnv.model.getModelIds();
        assertEquals(TestEnv.OtherUser.FILE_PNG, mEnv.model.getDocument(modelIds[0]));
    }

    @Test
    public void testLoadChildrenDocuments_failsWithNonRecentsAndEmptyStack() throws Exception {
        mEnv.state.stack.changeRoot(TestProvidersAccess.HOME);

        mEnv.mockProviders.get(TestProvidersAccess.HOME.authority)
                .setNextChildDocumentsReturns(TestEnv.FILE_APK, TestEnv.FILE_GIF);

        TestEventHandler<Model.Update> listener = new TestEventHandler<>();
        mEnv.model.addUpdateListener(listener::accept);

        mHandler.loadDocumentsForCurrentStack();
        CountDownLatch latch = new CountDownLatch(1);
        mEnv.model.addUpdateListener(event -> latch.countDown());
        mActivity.supportLoaderManager.runAsyncTaskLoader(AbstractActionHandler.LOADER_ID);

        latch.await(1, TimeUnit.SECONDS);
        assertTrue(listener.getLastValue().hasException());
    }

    @Test
    public void testPreviewItem_throwException() throws Exception {
        try {
            mHandler.previewItem(null);
            fail("Should have thrown UnsupportedOperationException.");
        } catch (UnsupportedOperationException expected) {
        }
    }
}
